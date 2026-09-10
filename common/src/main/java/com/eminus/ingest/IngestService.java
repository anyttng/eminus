package com.eminus.ingest;

import com.eminus.Eminus;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;
import com.eminus.work.WorkService;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;

public final class IngestService {
    private final WorkService<SectionPyramid> service;
    private final StateTable states;
    private final Dictionary<String> biomes;
    private final CellMerger merger;
    private final SectionDebounce debounce = new SectionDebounce(SectionDebounce.WINDOW_MILLIS);

    private volatile boolean running = true;

    public IngestService(WorkService<SectionPyramid> service, StateTable states, Dictionary<String> biomes,
            CellMerger merger) {
        this.service = service;
        this.states = states;
        this.biomes = biomes;
        this.merger = merger;
    }

    public void submitChunk(LevelChunk chunk) {
        LevelLightEngine light = chunk.getLevel().getLightEngine();
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();

        if (!hasLightData(light, chunk, chunkPos, sections.length)) {
            Eminus.LOGGER.debug("Chunk {} carries no light data; it is skipped.", chunkPos);
            return;
        }

        for (int index = 0; index < sections.length; index++) {
            if (sections[index].hasOnlyAir()) {
                continue;
            }

            submit(light, sections[index], chunkPos.x(), chunk.getSectionYFromSectionIndex(index), chunkPos.z());
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
        if (chunk == null || index < 0 || index >= chunk.getSections().length) {
            return;
        }

        submit(level.getLightEngine(), chunk.getSections()[index], sectionX, sectionY, sectionZ);
    }

    private void submit(LevelLightEngine light, LevelChunkSection section,
            int sectionX, int sectionY, int sectionZ) {
        SectionPos sectionPos = SectionPos.of(sectionX, sectionY, sectionZ);
        DataLayer skyLight = layer(light, LightLayer.SKY, sectionPos);
        DataLayer blockLight = layer(light, LightLayer.BLOCK, sectionPos);

        service.enqueue(pyramid -> {
            if (!running) {
                return;
            }

            SectionConverter.convert(section, skyLight, blockLight, states, biomes, pyramid);
            PyramidDownsampler.build(pyramid, states);
            merger.merge(pyramid, sectionX, sectionY, sectionZ);
        });
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
