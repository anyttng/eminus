package com.eminus.ingest;

import java.util.Objects;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

import org.jspecify.annotations.Nullable;

public final class LiveColumn implements IngestColumn {
    private final LevelChunk chunk;
    private final LevelLightEngine light;

    public LiveColumn(LevelChunk chunk) {
        this.chunk = chunk;
        this.light = chunk.getLevel().getLightEngine();
    }

    @Override
    public int chunkX() {
        return chunk.getPos().x();
    }

    @Override
    public int chunkZ() {
        return chunk.getPos().z();
    }

    @Override
    public int sectionCount() {
        return chunk.getSections().length;
    }

    @Override
    public int sectionY(int index) {
        return chunk.getSectionYFromSectionIndex(index);
    }

    @Override
    public boolean submits(int index) {
        if (!section(index).hasOnlyAir()) {
            return skyPublished(index);
        }

        return SectionConverter.lightDiffersFromBlank(sky(index), blockLight(index));
    }

    @Override
    public LevelChunkSection section(int index) {
        return chunk.getSections()[index];
    }

    @Override
    public BiomeWindow biomeWindow(int index) {
        return IngestService.biomeWindow(chunk, sectionY(index));
    }

    @Override
    public DataLayer skyLight(int index) {
        return Objects.requireNonNullElseGet(sky(index), () -> ColumnLight.openSky(hasSky()));
    }

    @Override
    public @Nullable DataLayer blockLight(int index) {
        return layer(LightLayer.BLOCK, sectionY(index));
    }

    public SectionPos sectionPos(int index) {
        return SectionPos.of(chunk.getPos(), sectionY(index));
    }

    public boolean lightOn() {
        return light.lightOnInColumn(SectionPos.getZeroNode(chunkX(), chunkZ()));
    }

    public boolean hasLightData() {
        for (int index = 0; index < sectionCount(); index++) {
            if (skyLayer(index) != null || blockLight(index) != null) {
                return true;
            }
        }

        return false;
    }

    public boolean hasSky() {
        return light.getLayerListener(LightLayer.SKY) != LayerLightEventListener.DummyLightLayerEventListener.INSTANCE;
    }

    public boolean skyPublished(int index) {
        return !hasSky() || skyLayer(index) != null;
    }

    public @Nullable DataLayer skyLayer(int index) {
        return layer(LightLayer.SKY, sectionY(index));
    }

    private @Nullable DataLayer sky(int index) {
        return ColumnLight.sky(this::layer, hasSky(), sectionY(index), light.getMaxLightSection());
    }

    private @Nullable DataLayer layer(LightLayer layer, int sectionY) {
        return light.getLayerListener(layer).getDataLayerData(SectionPos.of(chunkX(), sectionY, chunkZ()));
    }
}
