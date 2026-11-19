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

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
    private static final int[] NO_READS = new int[0];
    private static final long DIRTY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final long WAITING_NANOS = TimeUnit.SECONDS.toNanos(3);
    private static final int NEIGHBORS_KEPT = 4096;
    private static final int WAITING_KEPT = 256;

    private final SaveSource source;
    private final IngestService ingest;
    private final ColumnCoverage coverage;
    private final WorkService<SectionPyramid> service;
    private final long biomeZoomSeed;
    private final LongSet finished = new LongOpenHashSet();
    private final LongSet settled = new LongOpenHashSet();
    private final AtomicInteger imported = new AtomicInteger();
    private final AtomicInteger notSaved = new AtomicInteger();
    private final AtomicInteger notFull = new AtomicInteger();
    private final AtomicInteger unlit = new AtomicInteger();
    private final AtomicInteger noNeighbors = new AtomicInteger();
    private final AtomicInteger upgraded = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger parsed = new AtomicInteger();
    private final AtomicInteger coverageChecks = new AtomicInteger();
    private final AtomicInteger fromSnapshot = new AtomicInteger();
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    private final LongSet regions = new LongOpenHashSet();
    private final LongSet dirty = new LongOpenHashSet();
    private final Long2ObjectLinkedOpenHashMap<SavedChunk> neighbors = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectLinkedOpenHashMap<Waiting> waiting = new Long2ObjectLinkedOpenHashMap<>();
    private final LongSet refused = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<BitSet> heldBack = new Long2ObjectOpenHashMap<>();
    private boolean viewMoved;
    private long dirtySinceNanos;
    private boolean dirtyOverdue;
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

    private record Waiting(SavedChunk chunk, long sinceNanos) {
    }

    public record Tally(int tiles, int imported, int notSaved, int notFull, int unlit, int noNeighbors, int upgraded,
            int failed, int reads, int parsed, int coverageChecks, int fromSnapshot) {
    }

    public synchronized Tally tally() {
        return new Tally(tiles, imported.get(), notSaved.get(), notFull.get(), unlit.get(), noNeighbors.get(),
                upgraded.get(), failed.get(), reads.get(), parsed.get(), coverageChecks.get(), fromSnapshot.get());
    }

    public synchronized void tick(int playerChunkX, int playerChunkZ, int radiusChunks, int clientViewChunks) {
        boolean moved = playerChunkX != centerChunkX || playerChunkZ != centerChunkZ
                || clientViewChunks != viewChunks;
        centerChunkX = playerChunkX;
        centerChunkZ = playerChunkZ;
        viewChunks = clientViewChunks;
        long region = ImportTiles.regionOfChunk(playerChunkX, playerChunkZ);
        if (!running) {
            return;
        }

        viewMoved |= moved && !heldBack.isEmpty();
        expireWaiting();
        if (planned && region == plannedRegion && radiusChunks == plannedRadiusChunks) {
            replay(radiusChunks);
            return;
        }

        viewMoved = false;
        planned = true;
        planning = true;
        plannedRegion = region;
        plannedRadiusChunks = radiusChunks;
        service.enqueue(pyramid -> plan(playerChunkX, playerChunkZ, radiusChunks));
    }

    public void saved(SavedChunk chunk) {
        int chunkX = chunk.chunkX();
        int chunkZ = chunk.chunkZ();
        boolean importable = chunk.full() && chunk.lit() && !coverage.covers(chunkX, chunkZ);
        List<SavedColumn> ready = new ArrayList<>();
        synchronized (this) {
            if (!running) {
                return;
            }

            regions.add(ImportTiles.regionOfChunk(chunkX, chunkZ));
            if (chunk.hasBiomes()) {
                neighbors.putAndMoveToLast(ChunkPos.pack(chunkX, chunkZ), chunk.asNeighbor());
                if (neighbors.size() > NEIGHBORS_KEPT) {
                    neighbors.removeFirst();
                }
            }

            if (importable && !inView(chunkX, chunkZ)) {
                waiting.putAndMoveToLast(ChunkPos.pack(chunkX, chunkZ), new Waiting(chunk, System.nanoTime()));
                if (waiting.size() > WAITING_KEPT) {
                    markDirty(waiting.removeFirst().chunk());
                }
            }

            for (int dz = -BORDER_CHUNKS; dz <= BORDER_CHUNKS; dz++) {
                for (int dx = -BORDER_CHUNKS; dx <= BORDER_CHUNKS; dx++) {
                    long near = ChunkPos.pack(chunkX + dx, chunkZ + dz);
                    SavedColumn column = whole(near);
                    if (column != null) {
                        ready.add(column);
                    }

                    if (refused.contains(near)) {
                        markDirty(chunkX + dx, chunkZ + dz);
                    }
                }
            }
        }

        for (SavedColumn column : ready) {
            service.enqueue(pyramid -> importSnapshotColumn(column, pyramid));
        }
    }

    private void importSnapshotColumn(SavedColumn column, SectionPyramid pyramid) {
        try {
            if (ingest.ingestColumn(column, pyramid)) {
                imported.incrementAndGet();
                fromSnapshot.incrementAndGet();
            }
        } catch (RuntimeException failure) {
            failed(failure);
        }
    }

    private boolean inView(int chunkX, int chunkZ) {
        return Math.max(Math.abs(chunkX - centerChunkX), Math.abs(chunkZ - centerChunkZ)) <= viewChunks;
    }

    private @Nullable SavedColumn whole(long position) {
        Waiting held = waiting.get(position);
        if (held == null) {
            return null;
        }

        SavedChunk center = held.chunk();
        SavedChunk[] around = new SavedChunk[SavedColumn.NEIGHBOR_SIDE * SavedColumn.NEIGHBOR_SIDE];
        for (int dz = -BORDER_CHUNKS; dz <= BORDER_CHUNKS; dz++) {
            for (int dx = -BORDER_CHUNKS; dx <= BORDER_CHUNKS; dx++) {
                SavedChunk neighbor = dx == 0 && dz == 0 ? center
                        : neighbors.get(ChunkPos.pack(center.chunkX() + dx, center.chunkZ() + dz));
                if (neighbor == null) {
                    return null;
                }

                around[SavedColumn.neighborIndex(dx, dz)] = neighbor;
            }
        }

        waiting.remove(position);
        return new SavedColumn(center, around, source.hasSky(), biomeZoomSeed);
    }

    private void markDirty(SavedChunk chunk) {
        markDirty(chunk.chunkX(), chunk.chunkZ());
    }

    private void markDirty(int chunkX, int chunkZ) {
        if (dirty.isEmpty()) {
            dirtySinceNanos = System.nanoTime();
        }

        for (long tile : ImportTiles.tilesReading(chunkX, chunkZ)) {
            dirty.add(tile);
        }
    }

    private void expireWaiting() {
        long now = System.nanoTime();
        while (!waiting.isEmpty() && now - waiting.get(waiting.firstLongKey()).sinceNanos() >= WAITING_NANOS) {
            markDirty(waiting.removeFirst().chunk());
            dirtyOverdue = true;
        }
    }

    private void replay(int radiusChunks) {
        boolean idle = !planning && !scanning && current == null && next >= pending.length;
        if (!idle) {
            return;
        }

        boolean due = dirtyOverdue || System.nanoTime() - dirtySinceNanos >= DIRTY_DELAY_NANOS;
        if (!dirty.isEmpty() && due) {
            dirtyOverdue = false;
            forgetDirty();
            pending = ImportTiles.nearestFirst(dirty, centerChunkX, centerChunkZ, radiusChunks);
            dirty.clear();
        } else if (viewMoved) {
            viewMoved = false;
            pending = ImportTiles.nearestFirst(heldBack.keySet(), centerChunkX, centerChunkZ, radiusChunks);        } else {
            return;
        }

        next = 0;
        service.enqueue(pyramid -> advance());
    }

    private void forgetDirty() {
        finished.removeAll(dirty);
        for (long tile : dirty) {
            heldBack.remove(tile);
        }
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
            forgetDirty();
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
            started.begin();
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
            BitSet held = heldBack.get(tile);
            if (!settled.contains(tile) && (held != null || !finished.contains(tile))) {
                return new Candidate(tile, centerChunkX, centerChunkZ, viewChunks, held);
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
        BitSet held = new BitSet(ImportTiles.TILE_CHUNKS * ImportTiles.TILE_CHUNKS);
        BitSet asked = candidate.heldBack();
        int checked = 0;
        int minChunkX = ChunkPos.getX(candidate.tile()) << ImportTiles.TILE_BITS;
        int minChunkZ = ChunkPos.getZ(candidate.tile()) << ImportTiles.TILE_BITS;
        for (int dz = 0; dz < ImportTiles.TILE_CHUNKS; dz++) {
            for (int dx = 0; dx < ImportTiles.TILE_CHUNKS; dx++) {
                int column = dz * ImportTiles.TILE_CHUNKS + dx;
                if (asked != null && !asked.get(column)) {
                    continue;
                }

                int chunkX = minChunkX + dx;
                int chunkZ = minChunkZ + dz;
                boolean inView = Math.max(Math.abs(chunkX - candidate.centerChunkX()),
                        Math.abs(chunkZ - candidate.centerChunkZ())) <= candidate.viewChunks();
                if (inView) {
                    held.set(column);
                    continue;
                }

                checked++;
                if (!coverage.covers(chunkX, chunkZ)) {
                    wanted.set(column);
                }
            }
        }

        coverageChecks.addAndGet(checked);
        synchronized (this) {
            if (held.isEmpty()) {
                heldBack.remove(candidate.tile());
            } else {
                heldBack.put(candidate.tile(), held);
            }

            if (checked == ImportTiles.TILE_CHUNKS * ImportTiles.TILE_CHUNKS && wanted.isEmpty()) {
                settled.add(candidate.tile());
            }
        }

        return wanted;
    }

    private record Candidate(long tile, int centerChunkX, int centerChunkZ, int viewChunks,
            @Nullable BitSet heldBack) {
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
        private final BitSet asked = new BitSet(READ_CHUNKS);

        private int[] batch = NO_READS;
        private boolean neighborsAsked;
        private int nextRead;
        private int inFlight;
        private int readsDone;

        private TileRun(long tile, BitSet wanted) {
            this.tile = tile;
            this.wanted = wanted;
            this.minChunkX = (ChunkPos.getX(tile) << ImportTiles.TILE_BITS) - BORDER_CHUNKS;
            this.minChunkZ = (ChunkPos.getZ(tile) << ImportTiles.TILE_BITS) - BORDER_CHUNKS;
        }

        private void begin() {
            int[] centers = new int[wanted.cardinality()];
            int next = 0;
            for (int column = wanted.nextSetBit(0); column >= 0; column = wanted.nextSetBit(column + 1)) {
                centers[next++] = index(column % ImportTiles.TILE_CHUNKS + BORDER_CHUNKS,
                        column / ImportTiles.TILE_CHUNKS + BORDER_CHUNKS);
            }

            ask(centers);
        }

        private void ask(int[] indices) {
            synchronized (SaveImport.this) {
                for (int index : indices) {
                    asked.set(index);
                }

                batch = indices;
                nextRead = 0;
                readsDone = 0;
            }

            if (indices.length == 0) {
                onWorker(this::parse);
            } else {
                pump();
            }
        }

        @SuppressWarnings("FutureReturnValueIgnored")
        private void pump() {
            List<Integer> reads = new ArrayList<>(READS_IN_FLIGHT);
            boolean allRead = false;
            synchronized (SaveImport.this) {
                while (running && inFlight < READS_IN_FLIGHT && nextRead < batch.length) {
                    int index = batch[nextRead++];
                    if (regions.contains(ImportTiles.regionOfChunk(chunkX(index), chunkZ(index)))) {
                        inFlight++;
                        reads.add(index);
                    } else {
                        allRead = ++readsDone == batch.length;
                    }
                }
            }

            SaveImport.this.reads.addAndGet(reads.size());
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
                allRead = ++readsDone == batch.length;
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

            List<Integer> saved = new ArrayList<>(batch.length);
            for (int index : batch) {
                if (tags[index] != null) {
                    saved.add(index);
                }
            }

            if (saved.isEmpty()) {
                parsed();
                return;
            }

            AtomicInteger remaining = new AtomicInteger(saved.size());
            for (int index : saved) {
                service.enqueue(pyramid -> parseChunk(index, remaining));
            }
        }

        private void parseChunk(int index, AtomicInteger remaining) {
            try {
                CompoundTag tag = tags[index];
                tags[index] = null;
                chunks[index] = tag == null ? null : source.parse(tag);
                parsed.incrementAndGet();
            } catch (RuntimeException failure) {
                failed(failure);
            } finally {
                if (remaining.decrementAndGet() == 0) {
                    parsed();
                }
            }
        }

        private void parsed() {
            if (stopped()) {
                return;
            }

            if (neighborsAsked) {
                ingest();
                return;
            }

            neighborsAsked = true;
            ask(missingNeighbors());
        }

        private int[] missingNeighbors() {
            List<Integer> missing = new ArrayList<>();
            synchronized (SaveImport.this) {
                for (int column = wanted.nextSetBit(0); column >= 0; column = wanted.nextSetBit(column + 1)) {
                    int x = column % ImportTiles.TILE_CHUNKS + BORDER_CHUNKS;
                    int z = column / ImportTiles.TILE_CHUNKS + BORDER_CHUNKS;
                    SavedChunk center = chunks[index(x, z)];
                    if (center == null || !center.full() || !center.lit()) {
                        continue;
                    }

                    for (int dz = -BORDER_CHUNKS; dz <= BORDER_CHUNKS; dz++) {
                        for (int dx = -BORDER_CHUNKS; dx <= BORDER_CHUNKS; dx++) {
                            int near = index(x + dx, z + dz);
                            if (asked.get(near)) {
                                continue;
                            }

                            asked.set(near);
                            SavedChunk kept = neighbors.get(ChunkPos.pack(chunkX(near), chunkZ(near)));
                            if (kept == null) {
                                missing.add(near);
                            } else {
                                chunks[near] = kept;
                            }
                        }
                    }
                }
            }

            int[] indices = new int[missing.size()];
            for (int next = 0; next < indices.length; next++) {
                indices[next] = missing.get(next);
            }

            return indices;
        }

        private int index(int x, int z) {
            return z * SIDE_CHUNKS + x;
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
                service.enqueue(pyramid -> importColumn(column, pyramid, remaining));
            }
        }

        private void importColumn(SavedColumn column, SectionPyramid pyramid, AtomicInteger remaining) {
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
        }

        private @Nullable SavedColumn accept(int x, int z) {
            SavedChunk[] around = new SavedChunk[SavedColumn.NEIGHBOR_SIDE * SavedColumn.NEIGHBOR_SIDE];
            for (int dz = -BORDER_CHUNKS; dz <= BORDER_CHUNKS; dz++) {
                for (int dx = -BORDER_CHUNKS; dx <= BORDER_CHUNKS; dx++) {
                    around[SavedColumn.neighborIndex(dx, dz)] = chunks[(z + dz) * SIDE_CHUNKS + x + dx];
                }
            }

            SavedChunk center = around[SavedColumn.neighborIndex(0, 0)];
            SavedColumn.Verdict verdict = SavedColumn.judge(around);
            long position = ChunkPos.pack(minChunkX + x, minChunkZ + z);
            synchronized (SaveImport.this) {
                if (verdict == SavedColumn.Verdict.NO_NEIGHBORS) {
                    refused.add(position);
                } else {
                    refused.remove(position);
                }
            }

            switch (verdict) {
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
