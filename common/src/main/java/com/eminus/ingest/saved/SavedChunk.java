package com.eminus.ingest.saved;

import com.eminus.ingest.BiomeWindow;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import org.jspecify.annotations.Nullable;

public final class SavedChunk {
    private static final int LIGHT_SECTIONS_BELOW = 1;
    private static final int LIGHT_SECTIONS_BEYOND = 2;
    private static final int QUART_IN_SECTION_MASK = BiomeWindow.QUARTS_PER_SECTION - 1;

    private final int chunkX;
    private final int chunkZ;
    private final int minSectionY;
    private final @Nullable LevelChunkSection[] sections;
    private final @Nullable DataLayer[] skyLight;
    private final @Nullable DataLayer[] blockLight;
    private final boolean full;
    private final boolean hasBiomes;
    private final boolean lit;
    private final boolean upgraded;

    public SavedChunk(int chunkX, int chunkZ, int minSectionY, @Nullable LevelChunkSection[] sections,
            @Nullable DataLayer[] skyLight, @Nullable DataLayer[] blockLight, boolean full, boolean hasBiomes,
            boolean lit, boolean upgraded) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minSectionY = minSectionY;
        this.sections = sections;
        this.skyLight = skyLight;
        this.blockLight = blockLight;
        this.full = full;
        this.hasBiomes = hasBiomes;
        this.lit = lit;
        this.upgraded = upgraded;
    }

    public static SavedChunk of(SerializableChunkData data, int sectionCount, boolean upgraded) {
        int minSectionY = data.minSectionY();
        LevelChunkSection[] sections = new LevelChunkSection[sectionCount];
        DataLayer[] skyLight = new DataLayer[sectionCount + LIGHT_SECTIONS_BEYOND];
        DataLayer[] blockLight = new DataLayer[sectionCount + LIGHT_SECTIONS_BEYOND];
        for (SerializableChunkData.SectionData saved : data.sectionData()) {
            int index = saved.y() - minSectionY;
            if (index >= 0 && index < sectionCount) {
                sections[index] = saved.chunkSection();
            }

            int lightIndex = index + LIGHT_SECTIONS_BELOW;
            if (lightIndex >= 0 && lightIndex < skyLight.length) {
                skyLight[lightIndex] = saved.skyLight();
                blockLight[lightIndex] = saved.blockLight();
            }
        }

        ChunkStatus status = data.chunkStatus();
        return new SavedChunk(data.chunkPos().x(), data.chunkPos().z(), minSectionY, sections, skyLight, blockLight,
                status.isOrAfter(ChunkStatus.FULL), status.isOrAfter(ChunkStatus.BIOMES), data.lightCorrect(),
                upgraded);
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public int minSectionY() {
        return minSectionY;
    }

    public int sectionCount() {
        return sections.length;
    }

    public int maxLightSection() {
        return minSectionY - LIGHT_SECTIONS_BELOW + skyLight.length;
    }

    public boolean full() {
        return full;
    }

    public boolean hasBiomes() {
        return hasBiomes;
    }

    public boolean lit() {
        return lit;
    }

    public boolean upgraded() {
        return upgraded;
    }

    public @Nullable LevelChunkSection section(int index) {
        return sections[index];
    }

    public @Nullable DataLayer layer(LightLayer layer, int sectionY) {
        int index = sectionY - minSectionY + LIGHT_SECTIONS_BELOW;
        if (index < 0 || index >= skyLight.length) {
            return null;
        }

        return layer == LightLayer.SKY ? skyLight[index] : blockLight[index];
    }

    public @Nullable Holder<Biome> noiseBiome(int quartX, int quartY, int quartZ) {
        int quartMinY = QuartPos.fromSection(minSectionY);
        int quartMaxY = quartMinY + QuartPos.fromSection(sections.length) - 1;
        int clampedQuartY = Mth.clamp(quartY, quartMinY, quartMaxY);
        LevelChunkSection section = sections[QuartPos.toSection(clampedQuartY) - minSectionY];
        return section == null ? null : section.getNoiseBiome(quartX & QUART_IN_SECTION_MASK,
                clampedQuartY & QUART_IN_SECTION_MASK, quartZ & QUART_IN_SECTION_MASK);
    }
}
