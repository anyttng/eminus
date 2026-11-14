package com.eminus.ingest.saved;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.eminus.Eminus;
import com.eminus.cell.ColumnCoverage;
import com.eminus.ingest.IngestService;
import com.eminus.ingest.SectionPyramid;
import com.eminus.work.WorkService;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;

public final class SaveImport {
    public static final int READS_IN_FLIGHT = 8;

    private static final String JUDGED = "an accepted column has its own chunk";
    private static final int BORDER_CHUNKS = SavedColumn.NEIGHBOR_REACH;
    private static final int SIDE_CHUNKS = ImportTiles.TILE_CHUNKS + 2 * BORDER_CHUNKS;
    private static final int READ_CHUNKS = SIDE_CHUNKS * SIDE_CHUNKS;
    private static final long[] NO_TILES = new long[0];
    private static final long DIRTY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final SaveSource source;
    private final IngestService ingest;
    private final ColumnCoverage coverage;
    private final WorkService<SectionPyramid> service;
    private final long biomeZoomSeed;
    private final LongSet finished = new LongOpenHashSet();
    private final AtomicInteger imported = new AtomicInteger();
    private final AtomicInteger notSaved = new AtomicInteger();
    private final AtomicInteger notFull = new AtomicInteger();
    private final AtomicInteger unlit = new AtomicInteger();
    private final AtomicInteger noNeighbors = new AtomicInteger();
    private final AtomicInteger upgraded = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    private final LongSet regions = new LongOpenHashSet();
    private final LongSet dirty = new LongOpenHashSet();
    private long dirtySinceNanos;
    private long[] pending = NO_TILES;
    private int next;
    private int tiles;
    private @Nullable TileRun current;
    private boolean running = true;
    private boolean planned;
    private boolean planning;
    private boolean scanning;
    private int centerChunkX;
    private int centerChunkZ;
    private int viewChunks;
    private int plannedRadiusChunks;
    private long plannedRegion;

    public SaveImport(SaveSource source, IngestService ingest, ColumnCoverage coverage,
            WorkService<SectionPyramid> service, long biomeZoomSeed) {
        this.source = source;
        this.ingest = ingest;
        this.coverage = coverage;
        this.service = service;
        this.biomeZoomSeed = biomeZoomSeed;
    }

    public record Tally(int tiles, int imported, int notSaved, int notFull, int unlit, int noNeighbors, int upgraded,
            int failed) {
    }

    public synchronized Tally tally() {
        return new Tally(tiles, imported.get(), notSaved.get(), notFull.get(), unlit.get(), noNeighbors.get(),
                upgraded.get(), failed.get());
    }

    public synchronized void tick(int playerChunkX, int playerChunkZ, int radiusChunks, int clientViewChunks) {
        centerChunkX = playerChunkX;
        centerChunkZ = playerChunkZ;
        viewChunks = clientViewChunks;
        long region = ImportTiles.regionOfChunk(playerChunkX, playerChunkZ);
        if (!running) {
            return;
        }

        if (planned && region == plannedRegion && radiusChunks == plannedRadiusChunks) {
            replayDirty(radiusChunks);
            return;
        }

        planned = true;
        planning = true;
        plannedRegion = region;
        plannedRadiusChunks = radiusChunks;
        service.enqueue(pyramid -> plan(playerChunkX, playerChunkZ, radiusChunks));
    }

    public void saved(int chunkX, int chunkZ) {
        if (coverage.covers(chunkX, chunkZ)) {
            return;
        }

        synchronized (this) {
            if (!running) {
                return;
            }

            if (dirty.isEmpty()) {
                dirtySinceNanos = System.nanoTime();
            }

            regions.add(ImportTiles.regionOfChunk(chunkX, chunkZ));
            for (long tile : ImportTiles.tilesReading(chunkX, chunkZ)) {
                dirty.add(tile);
            }
        }
    }

    private void replayDirty(int radiusChunks) {
        boolean idle = !planning && !scanning && current == null && next >= pending.length;
        if (dirty.isEmpty() || !idle || System.nanoTime() - dirtySinceNanos < DIRTY_DELAY_NANOS) {
            return;
        }

        finished.removeAll(dirty);
        pending = ImportTiles.nearestFirst(dirty, centerChunkX, centerChunkZ, radiusChunks);
        next = 0;
        dirty.clear();
        service.enqueue(pyramid -> advance());
    }

    public synchronized void stop() {
        running = false;
    }

    private synchronized boolean stopped() {
        return !running;
    }

    private void plan(int playerChunkX, int playerChunkZ, int radiusChunks) {
        LongSet listed;
        try {
            listed = source.regions();
        } catch (RuntimeException failure) {
            synchronized (this) {
                planning = false;
            }

            throw failure;
        }

        LongSet known;
        synchronized (this) {
            regions.addAll(listed);
            finished.removeAll(dirty);
            dirty.clear();
            known = new LongOpenHashSet(regions);
        }

        long[] planned = ImportTiles.plan(known, playerChunkX, playerChunkZ, radiusChunks);
        synchronized (this) {
            pending = planned;
            next = 0;
            planning = false;
        }

        advance();
    }

    private void advance() {
        TileRun started = scan();
        if (started != null) {
            started.pump();
        }
    }

    private @Nullable TileRun scan() {
        synchronized (this) {
            if (!running || scanning || current != null) {
                return null;
            }

            scanning = true;
        }

        try {
            while (true) {
                Candidate candidate = nextCandidate();
                if (candidate == null) {
                    return null;
                }

                BitSet wanted = wanted(candidate);
                if (!wanted.isEmpty()) {
                    return start(candidate.tile(), wanted);
                }
            }
        } catch (RuntimeException failure) {
            synchronized (this) {
                scanning = false;
            }

            throw failure;
        }
    }

    private synchronized @Nullable Candidate nextCandidate() {
        while (running && next < pending.length) {
            long tile = pending[next++];
            if (!finished.contains(tile)) {
                return new Candidate(tile, centerChunkX, centerChunkZ, viewChunks);
            }
        }

        scanning = false;
        return null;
    }

    private synchronized @Nullable TileRun start(long tile, BitSet wanted) {
        scanning = false;
        if (!running) {
            return null;
        }

        TileRun started = new TileRun(tile, wanted);
        current = started;
        return started;
    }

    private BitSet wanted(Candidate candidate) {
        BitSet wanted = new BitSet(ImportTiles.TILE_CHUNKS * ImportTiles.TILE_CHUNKS);
        int minChunkX = ChunkPos.getX(candidate.tile()) << ImportTiles.TILE_BITS;
        int minChunkZ = ChunkPos.getZ(candidate.tile()) << ImportTiles.TILE_BITS;
        for (int dz = 0; dz < ImportTiles.TILE_CHUNKS; dz++) {
            for (int dx = 0; dx < ImportTiles.TILE_CHUNKS; dx++) {
                int chunkX = minChunkX + dx;
                int chunkZ = minChunkZ + dz;
                boolean inView = Math.max(Math.abs(chunkX - candidate.centerChunkX()),
                        Math.abs(chunkZ - candidate.centerChunkZ())) <= candidate.viewChunks();
                if (!inView && !coverage.covers(chunkX, chunkZ)) {
                    wanted.set(dz * ImportTiles.TILE_CHUNKS + dx);
                }
            }
        }

        return wanted;
    }

    private record Candidate(long tile, int centerChunkX, int centerChunkZ, int viewChunks) {
    }

    private void failed(RuntimeException failure) {
        failed.incrementAndGet();
        if (failureLogged.compareAndSet(false, true)) {
            Eminus.LOGGER.warn("A saved chunk could not be imported; further failures are only counted.", failure);
        }
    }

    private final class TileRun {
        private final long tile;
        private final BitSet wanted;
        private final int minChunkX;
        private final int minChunkZ;
        private final @Nullable CompoundTag[] tags = new CompoundTag[READ_CHUNKS];
        private final @Nullable SavedChunk[] chunks = new SavedChunk[READ_CHUNKS];

        private int nextRead;
        private int inFlight;
        private int readsDone;

        private TileRun(long tile, BitSet wanted) {
            this.tile = tile;
            this.wanted = wanted;
            this.minChunkX = (ChunkPos.getX(tile) << ImportTiles.TILE_BITS) - BORDER_CHUNKS;
            this.minChunkZ = (ChunkPos.getZ(tile) << ImportTiles.TILE_BITS) - BORDER_CHUNKS;
        }

        @SuppressWarnings("FutureReturnValueIgnored")
        private void pump() {
            List<Integer> reads = new ArrayList<>(READS_IN_FLIGHT);
            boolean allRead = false;
            synchronized (SaveImport.this) {
                while (running && inFlight < READS_IN_FLIGHT && nextRead < READ_CHUNKS) {
                    int index = nextRead++;
                    if (regions.contains(ImportTiles.regionOfChunk(chunkX(index), chunkZ(index)))) {
                        inFlight++;
                        reads.add(index);
                    } else {
                        allRead = ++readsDone == READ_CHUNKS;
                    }
                }
            }

            for (int index : reads) {
                source.read(chunkX(index), chunkZ(index))
                        .whenComplete((tag, failure) -> read(index, tag == null ? null : tag.orElse(null)));
            }

            if (allRead) {
                onWorker(this::parse);
            }
        }

        private void read(int index, @Nullable CompoundTag tag) {
            boolean allRead;
            synchronized (SaveImport.this) {
                tags[index] = tag;
                inFlight--;
                allRead = ++readsDone == READ_CHUNKS;
            }

            if (allRead) {
                onWorker(this::parse);
            } else {
                pump();
            }
        }

        private void onWorker(Runnable step) {
            if (!stopped()) {
                service.enqueue(pyramid -> step.run());
            }
        }

        private void parse() {
            if (stopped()) {
                return;
            }

            List<Integer> saved = new ArrayList<>(READ_CHUNKS);
            for (int index = 0; index < READ_CHUNKS; index++) {
                if (tags[index] != null) {
                    saved.add(index);
                }
            }

            if (saved.isEmpty()) {
                ingest();
                return;
            }

            AtomicInteger remaining = new AtomicInteger(saved.size());
            for (int index : saved) {
                service.enqueue(pyramid -> {
                    try {
                        CompoundTag tag = tags[index];
                        tags[index] = null;
                        chunks[index] = tag == null ? null : source.parse(tag);
                    } catch (RuntimeException failure) {
                        failed(failure);
                    } finally {
                        if (remaining.decrementAndGet() == 0) {
                            ingest();
                        }
                    }
                });
            }
        }

        private void ingest() {
            if (stopped()) {
                return;
            }

            List<SavedColumn> columns = new ArrayList<>(wanted.cardinality());
            for (int column = wanted.nextSetBit(0); column >= 0; column = wanted.nextSetBit(column + 1)) {
                SavedColumn accepted = accept(column % ImportTiles.TILE_CHUNKS + BORDER_CHUNKS,
                        column / ImportTiles.TILE_CHUNKS + BORDER_CHUNKS);
                if (accepted != null) {
                    columns.add(accepted);
                }
            }

            if (columns.isEmpty()) {
                finish();
                return;
            }

            AtomicInteger remaining = new AtomicInteger(columns.size());
            for (SavedColumn column : columns) {
                service.enqueue(pyramid -> {
                    try {
                        if (ingest.ingestColumn(column, pyramid)) {
                            imported.incrementAndGet();
                        }
                    } catch (RuntimeException failure) {
                        failed(failure);
                    } finally {
                        if (remaining.decrementAndGet() == 0) {
                            finish();
                        }
                    }
                });
            }
        }

        private @Nullable SavedColumn accept(int x, int z) {
            SavedChunk[] around = new SavedChunk[SavedColumn.NEIGHBOR_SIDE * SavedColumn.NEIGHBOR_SIDE];
            for (int dz = -BORDER_CHUNKS; dz <= BORDER_CHUNKS; dz++) {
                for (int dx = -BORDER_CHUNKS; dx <= BORDER_CHUNKS; dx++) {
                    around[SavedColumn.neighborIndex(dx, dz)] = chunks[(z + dz) * SIDE_CHUNKS + x + dx];
                }
            }

            SavedChunk center = around[SavedColumn.neighborIndex(0, 0)];
            switch (SavedColumn.judge(around)) {
                case NOT_SAVED -> notSaved.incrementAndGet();
                case NOT_FULL -> notFull.incrementAndGet();
                case UNLIT -> unlit.incrementAndGet();
                case NO_NEIGHBORS -> noNeighbors.incrementAndGet();
                case ACCEPTED -> {
                    if (Objects.requireNonNull(center, JUDGED).upgraded()) {
                        upgraded.incrementAndGet();
                    }

                    return new SavedColumn(center, around, source.hasSky(), biomeZoomSeed);
                }
            }

            return null;
        }

        private void finish() {
            synchronized (SaveImport.this) {
                finished.add(tile);
                tiles++;
                current = null;
            }

            onWorker(SaveImport.this::advance);
        }

        private int chunkX(int index) {
            return minChunkX + index % SIDE_CHUNKS;
        }

        private int chunkZ(int index) {
            return minChunkZ + index / SIDE_CHUNKS;
        }
    }
}
