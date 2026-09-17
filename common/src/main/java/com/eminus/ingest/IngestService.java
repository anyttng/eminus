package com.eminus.ingest;

import java.util.concurrent.atomic.AtomicInteger;

import com.eminus.Eminus;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;
import com.eminus.mixin.BiomeManagerAccessor;
import com.eminus.work.WorkService;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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

    public void submitChunk(LevelChunk chunk) {
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
        AtomicInteger remaining = new AtomicInteger(solidSections(sections));
        if (remaining.get() == 0) {
            service.enqueue(pyramid -> cover(chunkX, chunkZ));
            return;
        }

        for (int index = 0; index < sections.length; index++) {
            if (sections[index].hasOnlyAir()) {
                continue;
            }

            submit(light, chunk, index, remaining);
        }
    }

    public void markBlockChange(BlockPos pos, long now) {
        debounce.mark(SectionPos.asLong(pos), now);
    }

    public void pollDebounce(ClientLevel level, long now) {
        debounce.drain(now, sectionNode -> submitMarked(level, sectionNode));
    }

    public void stop() {
        running = false;
    }

    private void submitMarked(ClientLevel level, long sectionNode) {
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
            submitChunk(chunk);
            return;
        }

        submit(level.getLightEngine(), chunk, index, null);
    }

    private void submit(LevelLightEngine light, LevelChunk chunk, int index, @Nullable AtomicInteger remaining) {
        LevelChunkSection section = chunk.getSections()[index];
        int sectionX = chunk.getPos().x();
        int sectionY = chunk.getSectionYFromSectionIndex(index);
        int sectionZ = chunk.getPos().z();
        SectionPos sectionPos = SectionPos.of(sectionX, sectionY, sectionZ);
        DataLayer skyLight = layer(light, LightLayer.SKY, sectionPos);
        DataLayer blockLight = layer(light, LightLayer.BLOCK, sectionPos);
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
        return BiomeWindow.capture((quartX, quartY, quartZ) -> noiseBiome(chunk, around, quartX, quartY, quartZ),
                pos.x(), sectionY, pos.z(), seed);
    }

    private static Holder<Biome> noiseBiome(LevelChunk chunk, LevelChunk[] around, int quartX, int quartY,
            int quartZ) {
        ChunkPos pos = chunk.getPos();
        LevelChunk neighbour = around[neighbourIndex(QuartPos.toSection(quartX) - pos.x(),
                QuartPos.toSection(quartZ) - pos.z())];
        if (neighbour != null) {
            return neighbour.getNoiseBiome(quartX, quartY, quartZ);
        }

        return chunk.getNoiseBiome(ownQuart(quartX, pos.x()), quartY, ownQuart(quartZ, pos.z()));
    }

    private static int ownQuart(int quart, int chunk) {
        int first = QuartPos.fromSection(chunk);
        return Mth.clamp(quart, first, first + BiomeWindow.QUARTS_PER_SECTION - 1);
    }

    private static int neighbourIndex(int dx, int dz) {
        return (dz + NEIGHBOUR_REACH) * NEIGHBOUR_SIDE + dx + NEIGHBOUR_REACH;
    }

    private static int solidSections(LevelChunkSection[] sections) {
        int solid = 0;
        for (LevelChunkSection section : sections) {
            if (!section.hasOnlyAir()) {
                solid++;
            }
        }

        return solid;
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

    private static DataLayer layer(LevelLightEngine light, LightLayer layer, SectionPos sectionPos) {
        return light.getLayerListener(layer).getDataLayerData(sectionPos);
    }
}
