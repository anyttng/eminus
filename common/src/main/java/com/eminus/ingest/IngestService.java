package com.eminus.ingest;

import java.util.BitSet;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.eminus.Eminus;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;
import com.eminus.mixin.BiomeManagerAccessor;
import com.eminus.work.WorkService;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.jspecify.annotations.Nullable;

public final class IngestService {
    private static final int NEIGHBOR_REACH = 1;
    private static final int NEIGHBOR_SIDE = 2 * NEIGHBOR_REACH + 1;

    private final WorkService<SectionPyramid> service;
    private final StateTable states;
    private final Dictionary<String> biomes;
    private final CellMerger merger;
    private final ColumnCoverage coverage;
    private final CellChangeListener listener;
    private final SectionDebounce debounce = new SectionDebounce(SectionDebounce.WINDOW_MILLIS);
    private final LongOpenHashSet deferred = new LongOpenHashSet();
    private final LongOpenHashSet lightOnly = new LongOpenHashSet();
    private final LightDigests digests = new LightDigests();
    private final Queue<Long> stale = new ConcurrentLinkedQueue<>();
    private final Object flight = new Object();

    private volatile boolean running = true;
    private int inFlight;

    public IngestService(WorkService<SectionPyramid> service, StateTable states, Dictionary<String> biomes,
            CellMerger merger, ColumnCoverage coverage, CellChangeListener listener) {
        this.service = service;
        this.states = states;
        this.biomes = biomes;
        this.merger = merger;
        this.coverage = coverage;
        this.listener = listener;
    }

    public int queued() {
        return service.pending();
    }

    public int pendingBlockChanges() {
        return debounce.size();
    }

    public void submitChunk(LevelChunk chunk, IngestTrigger trigger) {
        ChunkPos chunkPos = chunk.getPos();
        if (trigger == IngestTrigger.UNLOAD) {
            flushMarked(chunk);
        }

        retryDeferred(chunk.getLevel(), chunkPos.x, chunkPos.z);
        ingestChunk(chunk, trigger);
        if (trigger == IngestTrigger.UNLOAD) {
            digests.forgetColumn(chunkPos.x, chunkPos.z, chunk.getMinSection(), chunk.getMaxSection() - 1);
        }
    }

    public void submitBiomeChange(Level level, Collection<ChunkPos> changed) {
        LongOpenHashSet reached = new LongOpenHashSet();
        for (ChunkPos pos : changed) {
            for (int dz = -NEIGHBOR_REACH; dz <= NEIGHBOR_REACH; dz++) {
                for (int dx = -NEIGHBOR_REACH; dx <= NEIGHBOR_REACH; dx++) {
                    int chunkX = pos.x + dx;
                    int chunkZ = pos.z + dz;
                    LevelChunk chunk = reached.add(column(chunkX, chunkZ))
                            ? level.getChunkSource().getChunkNow(chunkX, chunkZ) : null;
                    if (chunk != null) {
                        submitChunk(chunk, IngestTrigger.BIOME);
                    }
                }
            }
        }
    }

    private void flushMarked(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        boolean covered = coverage.covers(chunkPos.x, chunkPos.z);
        debounce.takeColumn(chunkPos.x, chunkPos.z, sectionNode -> {
            IngestTrigger trigger = markedTrigger(sectionNode);
            if (covered) {
                submitMarked(chunk, sectionNode, trigger);
            }
        });
    }

    private void retryDeferred(Level level, int chunkX, int chunkZ) {
        for (int dz = -NEIGHBOR_REACH; dz <= NEIGHBOR_REACH; dz++) {
            for (int dx = -NEIGHBOR_REACH; dx <= NEIGHBOR_REACH; dx++) {
                if ((dx != 0 || dz != 0) && deferred.remove(column(chunkX + dx, chunkZ + dz))) {
                    LevelChunk waiting = level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz);
                    if (waiting != null) {
                        ingestChunk(waiting, IngestTrigger.NEIGHBOR);
                    }
                }
            }
        }
    }

    private void ingestChunk(LevelChunk chunk, IngestTrigger trigger) {
        LiveColumn column = new LiveColumn(chunk);
        if (!ready(chunk, column)) {
            return;
        }

        boolean[] submitted = pickSections(column);
        if (trigger != IngestTrigger.UNLOAD && coverage.holds(column.chunkX(), column.chunkZ())) {
            probeSkippedSections(chunk, submitted);
        }

        ingestSections(column, submitted, trigger);
    }

    void ingestSections(IngestColumn column, boolean[] submitted, IngestTrigger trigger) {
        int chunkX = column.chunkX();
        int chunkZ = column.chunkZ();
        int count = 0;
        for (boolean picked : submitted) {
            if (picked) {
                count++;
            }
        }

        if (count == 0) {
            service.enqueue(pyramid -> whileRunning(() -> cover(chunkX, chunkZ)));
            return;
        }

        WholeChunkIngest whole = new WholeChunkIngest(count);
        for (int index = 0; index < submitted.length; index++) {
            if (submitted[index]) {
                submit(column, index, whole, trigger);
            }
        }
    }

    private boolean ready(LevelChunk chunk, LiveColumn column) {
        ChunkPos chunkPos = chunk.getPos();
        if (ChunkRefusals.refuses(chunk.getLevel(), chunkPos.x, chunkPos.z)) {
            return false;
        }

        if (!column.lightOn()) {
            Eminus.LOGGER.debug("Chunk {} has no light applied yet; it waits for its light trigger.", chunkPos);
            return false;
        }

        if (!column.hasLightData()) {
            Eminus.LOGGER.debug("Chunk {} carries no light data; it is skipped.", chunkPos);
            return false;
        }

        if (!neighborsLoaded(chunk.getLevel(), chunkPos.x, chunkPos.z)) {
            deferred.add(column(chunkPos.x, chunkPos.z));
            return false;
        }

        return true;
    }

    static boolean[] pickSections(IngestColumn column) {
        boolean[] submitted = new boolean[column.sectionCount()];
        for (int index = 0; index < submitted.length; index++) {
            submitted[index] = column.submits(index);
        }

        return submitted;
    }

    private void probeSkippedSections(LevelChunk chunk, boolean[] submitted) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            if (!submitted[index] && sections[index].hasOnlyAir()) {
                probeSkipped(chunk.getPos().x, chunk.getSectionYFromSectionIndex(index), chunk.getPos().z);
            }
        }
    }

    private void probeSkipped(int sectionX, int sectionY, int sectionZ) {
        service.enqueue(pyramid -> whileRunning(() -> {
            if (merger.storesBeyondOpenSky(sectionX, sectionY, sectionZ)) {
                stale.add(SectionPos.asLong(sectionX, sectionY, sectionZ));
            }
        }));
    }

    public void markBlockChange(BlockPos pos, long now) {
        markChanged(SectionPos.asLong(pos), now);
    }

    private void markChanged(long sectionNode, long now) {
        lightOnly.remove(sectionNode);
        debounce.mark(sectionNode, now);
    }

    public void markLightUpdate(SectionPos pos, long now) {
        if (!coverage.covers(pos.x(), pos.z())) {
            return;
        }

        long sectionNode = pos.asLong();
        if (!debounce.holds(sectionNode)) {
            lightOnly.add(sectionNode);
        }

        debounce.mark(sectionNode, now);
    }

    public void markLightPacket(int chunkX, int chunkZ, BitSet sections, int minSectionY, long now) {
        if (!coverage.covers(chunkX, chunkZ)) {
            return;
        }

        for (int index = sections.nextSetBit(0); index >= 0; index = sections.nextSetBit(index + 1)) {
            markLightUpdate(SectionPos.of(chunkX, minSectionY + index, chunkZ), now);
        }
    }

    public void pollDebounce(ClientLevel level, long now) {
        for (Long sectionNode = stale.poll(); sectionNode != null; sectionNode = stale.poll()) {
            markChanged(sectionNode, now);
        }

        debounce.drain(now, sectionNode -> submitMarked(level, sectionNode));
        deferred.removeIf(
                column -> level.getChunkSource().getChunkNow(SectionPos.x(column), SectionPos.z(column)) == null);
    }

    public void stop() {
        boolean interrupted = false;
        synchronized (flight) {
            running = false;
            while (inFlight > 0) {
                try {
                    flight.wait();
                } catch (InterruptedException shutdown) {
                    interrupted = true;
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        digests.clear();
        stale.clear();
    }

    private void whileRunning(Runnable work) {
        if (!enter()) {
            return;
        }

        try {
            work.run();
        } finally {
            exit();
        }
    }

    private boolean enter() {
        synchronized (flight) {
            if (running) {
                inFlight++;
            }

            return running;
        }
    }

    private void exit() {
        synchronized (flight) {
            inFlight--;
            if (inFlight == 0) {
                flight.notifyAll();
            }
        }
    }

    private void submitMarked(ClientLevel level, long sectionNode) {
        IngestTrigger trigger = markedTrigger(sectionNode);
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.x(sectionNode), SectionPos.z(sectionNode));
        if (chunk != null) {
            submitMarked(chunk, sectionNode, trigger);
        }
    }

    private IngestTrigger markedTrigger(long sectionNode) {
        return lightOnly.remove(sectionNode) ? IngestTrigger.LIGHT : IngestTrigger.BLOCK;
    }

    private void submitMarked(LevelChunk chunk, long sectionNode, IngestTrigger trigger) {
        int sectionX = SectionPos.x(sectionNode);
        int sectionZ = SectionPos.z(sectionNode);
        int index = chunk.getSectionIndexFromSectionY(SectionPos.y(sectionNode));
        LiveColumn column = new LiveColumn(chunk);
        if (index < 0 || index >= chunk.getSections().length || !column.lightOn()
                || ChunkRefusals.refuses(chunk.getLevel(), sectionX, sectionZ)) {
            return;
        }

        if (!coverage.holds(sectionX, sectionZ)) {
            submitChunk(chunk, trigger);
            return;
        }

        // The light engine drops the layers of an air-only section whose 26 neighbors are air-only too, for good.
        if (!column.section(index).hasOnlyAir() && !column.skyPublished(index)) {
            return;
        }

        submit(column, index, null, trigger);
        submitRunBelow(column, index);
    }

    private void submitRunBelow(LiveColumn column, int index) {
        if (!column.hasSky()) {
            return;
        }

        for (int below = index - 1; below >= 0; below--) {
            if (!column.section(below).hasOnlyAir() || column.skyLayer(below) != null) {
                return;
            }

            submit(column, below, null, IngestTrigger.LIGHT);
        }
    }

    private void submit(IngestColumn column, int index, @Nullable WholeChunkIngest whole, IngestTrigger trigger) {
        LevelChunkSection section = column.section(index);
        int sectionX = column.chunkX();
        int sectionY = column.sectionY(index);
        int sectionZ = column.chunkZ();
        DataLayer skyLight = column.skyLight(index);
        DataLayer blockLight = column.blockLight(index);

        boolean differs = digests.record(SectionPos.asLong(sectionX, sectionY, sectionZ), skyLight, blockLight);
        if (trigger == IngestTrigger.LIGHT && !differs) {
            return;
        }

        BiomeWindow window = column.biomeWindow(index);
        boolean everyLevel = !coverage.covers(sectionX, sectionZ);

        service.enqueue(pyramid -> whileRunning(() -> {
            boolean merged = false;
            try {
                SectionConverter.convert(section, window, skyLight, blockLight, states, biomes, pyramid);

                PyramidDownsampler.build(pyramid, states, states);
                if (everyLevel) {
                    merger.mergeEveryLevel(pyramid, sectionX, sectionY, sectionZ);
                } else {
                    merger.merge(pyramid, sectionX, sectionY, sectionZ);
                }

                merged = true;
            } finally {
                if (whole != null && whole.sectionDone(merged)) {
                    cover(sectionX, sectionZ);
                }
            }
        }));
    }

    public boolean ingestColumn(IngestColumn column, SectionPyramid pyramid) {
        if (!enter()) {
            return false;
        }

        try {
            return mergeColumn(column, pyramid);
        } finally {
            exit();
        }
    }

    private boolean mergeColumn(IngestColumn column, SectionPyramid pyramid) {
        if (coverage.covers(column.chunkX(), column.chunkZ())) {
            return false;
        }

        for (int index = 0; index < column.sectionCount(); index++) {
            if (!running) {
                return false;
            }

            if (column.submits(index)) {
                SectionConverter.convert(column.section(index), column.biomeWindow(index), column.skyLight(index),
                        column.blockLight(index), states, biomes, pyramid);
                PyramidDownsampler.build(pyramid, states, states);
                merger.mergeEveryLevel(pyramid, column.chunkX(), column.sectionY(index), column.chunkZ());
            }
        }

        cover(column.chunkX(), column.chunkZ());
        return true;
    }

    private void cover(int chunkX, int chunkZ) {
        if (coverage.cover(chunkX, chunkZ)) {
            try {
                coverage.persist(chunkX, chunkZ);
            } catch (RuntimeException failure) {
                Eminus.LOGGER.error("Could not store column {}, {} as covered; it is ingested again after a restart.",
                        chunkX, chunkZ, failure);
            }

            listener.covered(chunkX, chunkZ);
        }
    }

    @SuppressWarnings("NullAway")
    static BiomeWindow biomeWindow(LevelChunk chunk, int sectionY) {
        Level level = chunk.getLevel();
        ChunkPos pos = chunk.getPos();
        LevelChunk[] around = new LevelChunk[NEIGHBOR_SIDE * NEIGHBOR_SIDE];
        for (int dz = -NEIGHBOR_REACH; dz <= NEIGHBOR_REACH; dz++) {
            for (int dx = -NEIGHBOR_REACH; dx <= NEIGHBOR_REACH; dx++) {
                around[neighborIndex(dx, dz)] = dx == 0 && dz == 0
                        ? chunk
                        : level.getChunkSource().getChunkNow(pos.x + dx, pos.z + dz);
            }
        }

        long seed = ((BiomeManagerAccessor) level.getBiomeManager()).eminus$biomeZoomSeed();
        return BiomeWindow.capture((quartX, quartY, quartZ) -> noiseBiome(pos, around, quartX, quartY, quartZ),
                pos.x, sectionY, pos.z, seed);
    }

    private static @Nullable Holder<Biome> noiseBiome(ChunkPos pos, LevelChunk[] around, int quartX, int quartY,
            int quartZ) {
        LevelChunk neighbor = around[neighborIndex(QuartPos.toSection(quartX) - pos.x,
                QuartPos.toSection(quartZ) - pos.z)];
        return neighbor == null ? null : neighbor.getNoiseBiome(quartX, quartY, quartZ);
    }

    private static boolean neighborsLoaded(Level level, int chunkX, int chunkZ) {
        for (int dz = -NEIGHBOR_REACH; dz <= NEIGHBOR_REACH; dz++) {
            for (int dx = -NEIGHBOR_REACH; dx <= NEIGHBOR_REACH; dx++) {
                if (level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    private static long column(int chunkX, int chunkZ) {
        return SectionPos.getZeroNode(chunkX, chunkZ);
    }

    private static int neighborIndex(int dx, int dz) {
        return (dz + NEIGHBOR_REACH) * NEIGHBOR_SIDE + dx + NEIGHBOR_REACH;
    }

}
