package com.eminus.ingest;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

import org.jspecify.annotations.Nullable;

public final class IngestService {
    private static final int NEIGHBOUR_REACH = 1;
    private static final int NEIGHBOUR_SIDE = 2 * NEIGHBOUR_REACH + 1;

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

    private volatile boolean running = true;

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
        retryDeferred(chunk.getLevel(), chunkPos.x(), chunkPos.z());
        ingestChunk(chunk, trigger);
    }

    private void retryDeferred(Level level, int chunkX, int chunkZ) {
        for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
            for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
                if ((dx != 0 || dz != 0) && deferred.remove(column(chunkX + dx, chunkZ + dz))) {
                    LevelChunk waiting = level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz);
                    if (waiting != null) {
                        ingestChunk(waiting, IngestTrigger.NEIGHBOUR);
                    }
                }
            }
        }
    }

    private void ingestChunk(LevelChunk chunk, IngestTrigger trigger) {
        LevelLightEngine light = chunk.getLevel().getLightEngine();
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();

        if (!lightOn(light, chunkPos.x(), chunkPos.z())) {
            Eminus.LOGGER.debug("Chunk {} has no light applied yet; it waits for its light trigger.", chunkPos);
            return;
        }

        if (!hasLightData(light, chunk, chunkPos, sections.length)) {
            Eminus.LOGGER.debug("Chunk {} carries no light data; it is skipped.", chunkPos);
            return;
        }

        int chunkX = chunkPos.x();
        int chunkZ = chunkPos.z();
        if (!neighboursLoaded(chunk.getLevel(), chunkX, chunkZ)) {
            deferred.add(column(chunkX, chunkZ));
            return;
        }

        boolean[] submitted = new boolean[sections.length];
        int count = 0;
        for (int index = 0; index < sections.length; index++) {
            submitted[index] = submits(light, chunk, index);
            if (submitted[index]) {
                count++;
            }
        }

        if (trigger != IngestTrigger.UNLOAD && coverage.covers(chunkX, chunkZ)) {
            for (int index = 0; index < sections.length; index++) {
                if (!submitted[index] && sections[index].hasOnlyAir()) {
                    probeSkipped(chunkX, chunk.getSectionYFromSectionIndex(index), chunkZ);
                }
            }
        }

        if (count == 0) {
            service.enqueue(pyramid -> cover(chunkX, chunkZ));
            return;
        }

        AtomicInteger remaining = new AtomicInteger(count);
        for (int index = 0; index < sections.length; index++) {
            if (submitted[index]) {
                submit(light, chunk, index, remaining, trigger);
            }
        }
    }

    private void probeSkipped(int sectionX, int sectionY, int sectionZ) {
        service.enqueue(pyramid -> {
            if (running && merger.storesBeyondOpenSky(sectionX, sectionY, sectionZ)) {
                stale.add(SectionPos.asLong(sectionX, sectionY, sectionZ));
            }
        });
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

    public void pollDebounce(ClientLevel level, long now) {
        for (Long sectionNode = stale.poll(); sectionNode != null; sectionNode = stale.poll()) {
            markChanged(sectionNode, now);
        }

        debounce.drain(now, sectionNode -> submitMarked(level, sectionNode));
        deferred.removeIf(
                column -> level.getChunkSource().getChunkNow(SectionPos.x(column), SectionPos.z(column)) == null);
    }

    public void stop() {
        running = false;
        digests.clear();
        stale.clear();
    }

    private void submitMarked(ClientLevel level, long sectionNode) {
        IngestTrigger trigger = lightOnly.remove(sectionNode) ? IngestTrigger.LIGHT : IngestTrigger.BLOCK;
        int sectionX = SectionPos.x(sectionNode);
        int sectionY = SectionPos.y(sectionNode);
        int sectionZ = SectionPos.z(sectionNode);
        LevelChunk chunk = level.getChunkSource().getChunkNow(sectionX, sectionZ);
        int index = chunk == null ? -1 : chunk.getSectionIndexFromSectionY(sectionY);
        if (chunk == null || index < 0 || index >= chunk.getSections().length
                || !lightOn(level.getLightEngine(), sectionX, sectionZ)) {
            return;
        }

        if (!coverage.covers(sectionX, sectionZ)) {
            submitChunk(chunk, trigger);
            return;
        }

        // The light engine drops the layers of an air-only section whose 26 neighbours are air-only too, for good.
        if (!chunk.getSections()[index].hasOnlyAir()
                && !skyPublished(level.getLightEngine(), SectionPos.of(sectionX, sectionY, sectionZ))) {
            return;
        }

        submit(level.getLightEngine(), chunk, index, null, trigger);
    }

    private void submit(LevelLightEngine light, LevelChunk chunk, int index, @Nullable AtomicInteger remaining,
            IngestTrigger trigger) {
        LevelChunkSection section = chunk.getSections()[index];
        int sectionX = chunk.getPos().x();
        int sectionY = chunk.getSectionYFromSectionIndex(index);
        int sectionZ = chunk.getPos().z();
        SectionPos sectionPos = SectionPos.of(sectionX, sectionY, sectionZ);
        DataLayer skyLight = skyLayer(light, chunk.getLevel(), sectionPos);
        DataLayer blockLight = layer(light, LightLayer.BLOCK, sectionPos);

        boolean differs = digests.record(sectionPos.asLong(), skyLight, blockLight);
        if (trigger == IngestTrigger.LIGHT && !differs) {
            return;
        }

        BiomeWindow window = biomeWindow(chunk, sectionY);

        service.enqueue(pyramid -> {
            if (!running) {
                return;
            }

            try {
                SectionConverter.convert(section, window, skyLight, blockLight, states, biomes, pyramid);

                PyramidDownsampler.build(pyramid, states);
                merger.merge(pyramid, sectionX, sectionY, sectionZ);
            } finally {
                if (remaining != null && remaining.decrementAndGet() == 0) {
                    cover(sectionX, sectionZ);
                }
            }
        });
    }

    private void cover(int chunkX, int chunkZ) {
        if (running && coverage.cover(chunkX, chunkZ)) {
            coverage.persist(chunkX, chunkZ);
            listener.covered(chunkX, chunkZ);
        }
    }

    private static BiomeWindow biomeWindow(LevelChunk chunk, int sectionY) {
        Level level = chunk.getLevel();
        ChunkPos pos = chunk.getPos();
        LevelChunk[] around = new LevelChunk[NEIGHBOUR_SIDE * NEIGHBOUR_SIDE];
        for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
            for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
                around[neighbourIndex(dx, dz)] = dx == 0 && dz == 0
                        ? chunk
                        : level.getChunkSource().getChunkNow(pos.x() + dx, pos.z() + dz);
            }
        }

        long seed = ((BiomeManagerAccessor) level.getBiomeManager()).eminus$biomeZoomSeed();
        return BiomeWindow.capture((quartX, quartY, quartZ) -> noiseBiome(pos, around, quartX, quartY, quartZ),
                pos.x(), sectionY, pos.z(), seed);
    }

    private static @Nullable Holder<Biome> noiseBiome(ChunkPos pos, LevelChunk[] around, int quartX, int quartY,
            int quartZ) {
        LevelChunk neighbour = around[neighbourIndex(QuartPos.toSection(quartX) - pos.x(),
                QuartPos.toSection(quartZ) - pos.z())];
        return neighbour == null ? null : neighbour.getNoiseBiome(quartX, quartY, quartZ);
    }

    private static boolean neighboursLoaded(Level level, int chunkX, int chunkZ) {
        for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
            for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
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

    private static int neighbourIndex(int dx, int dz) {
        return (dz + NEIGHBOUR_REACH) * NEIGHBOUR_SIDE + dx + NEIGHBOUR_REACH;
    }

    private static boolean submits(LevelLightEngine light, LevelChunk chunk, int index) {
        SectionPos sectionPos = SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(index));
        if (!skyPublished(light, sectionPos)) {
            return false;
        }

        if (!chunk.getSections()[index].hasOnlyAir()) {
            return true;
        }

        return SectionConverter.lightDiffersFromBlank(layer(light, LightLayer.SKY, sectionPos),
                layer(light, LightLayer.BLOCK, sectionPos));
    }

    private static boolean skyPublished(LevelLightEngine light, SectionPos sectionPos) {
        return light.getLayerListener(LightLayer.SKY) == LayerLightEventListener.DummyLightLayerEventListener.INSTANCE
                || layer(light, LightLayer.SKY, sectionPos) != null;
    }

    private static boolean lightOn(LevelLightEngine light, int chunkX, int chunkZ) {
        return light.lightOnInColumn(SectionPos.getZeroNode(chunkX, chunkZ));
    }

    private static boolean hasLightData(LevelLightEngine light, LevelChunk chunk, ChunkPos chunkPos, int sectionCount) {
        for (int index = 0; index < sectionCount; index++) {
            SectionPos sectionPos = SectionPos.of(chunkPos, chunk.getSectionYFromSectionIndex(index));
            if (layer(light, LightLayer.SKY, sectionPos) != null || layer(light, LightLayer.BLOCK, sectionPos) != null) {
                return true;
            }
        }

        return false;
    }

    private static @Nullable DataLayer layer(LevelLightEngine light, LightLayer layer, SectionPos sectionPos) {
        return light.getLayerListener(layer).getDataLayerData(sectionPos);
    }

    private static DataLayer skyLayer(LevelLightEngine light, Level level, SectionPos sectionPos) {
        DataLayer stored = layer(light, LightLayer.SKY, sectionPos);
        return stored != null ? stored : skyFromGame(level, sectionPos);
    }

    private static DataLayer skyFromGame(Level level, SectionPos sectionPos) {
        DataLayer built = new DataLayer();
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        int originX = sectionPos.minBlockX();
        int originY = sectionPos.minBlockY();
        int originZ = sectionPos.minBlockZ();

        for (int z = 0; z < SectionPyramid.SECTION_SIDE; z++) {
            for (int x = 0; x < SectionPyramid.SECTION_SIDE; x++) {
                block.set(originX + x, originY, originZ + z);
                int value = level.getBrightness(LightLayer.SKY, block);
                if (value == 0) {
                    continue;
                }

                for (int y = 0; y < SectionPyramid.SECTION_SIDE; y++) {
                    built.set(x, y, z, value);
                }
            }
        }

        return built;
    }
}
