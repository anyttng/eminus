package com.eminus.ingest.saved;

import java.util.BitSet;

import com.eminus.ingest.BiomeWindow;
import com.eminus.mixin.ChunkSerializerAccessor;
import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

import org.jspecify.annotations.Nullable;

public final class SavedChunk {
    private static final int LIGHT_SECTIONS_BELOW = 1;
    private static final int LIGHT_SECTIONS_BEYOND = 2;
    private static final int QUART_IN_SECTION_MASK = BiomeWindow.QUARTS_PER_SECTION - 1;
    private static final String STATUS_TAG = "Status";
    private static final String SECTION_Y_TAG = "Y";
    private static final String BLOCK_STATES_TAG = "block_states";
    private static final String BIOMES_TAG = "biomes";

    private final int chunkX;
    private final int chunkZ;
    private final int minSectionY;
    private final @Nullable LevelChunkSection[] sections;
    private final @Nullable DataLayer[] skyLight;
    private final @Nullable DataLayer[] blockLight;
    private final @Nullable PalettedContainerRO<Holder<Biome>>[] biomes;
    private final BitSet occupied;
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
        this.biomes = biomesOf(sections);
        this.occupied = new BitSet(sections.length);
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            occupied.set(index, section != null && !section.hasOnlyAir());
        }

        this.full = full;
        this.hasBiomes = hasBiomes;
        this.lit = lit;
        this.upgraded = upgraded;
    }

    private SavedChunk(SavedChunk whole) {
        this.chunkX = whole.chunkX;
        this.chunkZ = whole.chunkZ;
        this.minSectionY = whole.minSectionY;
        this.sections = new LevelChunkSection[whole.sections.length];
        this.skyLight = new DataLayer[0];
        this.blockLight = new DataLayer[0];
        this.biomes = whole.biomes;
        this.occupied = whole.occupied;
        this.full = false;
        this.hasBiomes = whole.hasBiomes;
        this.lit = false;
        this.upgraded = whole.upgraded;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable PalettedContainerRO<Holder<Biome>>[] biomesOf(@Nullable LevelChunkSection[] sections) {
        PalettedContainerRO<Holder<Biome>>[] biomes = new PalettedContainerRO[sections.length];
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            biomes[index] = section == null ? null : section.getBiomes();
        }

        return biomes;
    }

    public SavedChunk asNeighbor() {
        return new SavedChunk(this);
    }

    public static @Nullable SavedChunk of(ServerLevel level, CompoundTag chunkTag, boolean upgraded) {
        String saved = chunkTag.getString(STATUS_TAG);
        if (saved.isEmpty()) {
            return null;
        }

        ChunkPos chunkPos = new ChunkPos(chunkTag.getInt(ChunkSerializer.X_POS_TAG),
                chunkTag.getInt(ChunkSerializer.Z_POS_TAG));
        int minSectionY = level.getMinSection();
        int sectionCount = level.getSectionsCount();
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = ChunkSerializerAccessor.eminus$makeBiomeCodec(biomes);
        LevelChunkSection[] sections = new LevelChunkSection[sectionCount];
        DataLayer[] skyLight = new DataLayer[sectionCount + LIGHT_SECTIONS_BEYOND];
        DataLayer[] blockLight = new DataLayer[sectionCount + LIGHT_SECTIONS_BEYOND];
        ListTag sectionTags = chunkTag.getList(ChunkSerializer.SECTIONS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < sectionTags.size(); i++) {
            CompoundTag sectionTag = sectionTags.getCompound(i);
            int sectionY = sectionTag.getByte(SECTION_Y_TAG);
            int index = sectionY - minSectionY;
            if (index >= 0 && index < sectionCount) {
                sections[index] = new LevelChunkSection(blockStates(sectionTag, chunkPos, sectionY),
                        biomes(sectionTag, chunkPos, sectionY, biomes, biomeCodec));
            }

            int lightIndex = index + LIGHT_SECTIONS_BELOW;
            if (lightIndex >= 0 && lightIndex < skyLight.length) {
                skyLight[lightIndex] = layer(sectionTag, ChunkSerializer.SKY_LIGHT_TAG);
                blockLight[lightIndex] = layer(sectionTag, ChunkSerializer.BLOCK_LIGHT_TAG);
            }
        }

        ChunkStatus status = ChunkStatus.byName(saved);
        return new SavedChunk(chunkPos.x, chunkPos.z, minSectionY, sections, skyLight, blockLight,
                status.isOrAfter(ChunkStatus.FULL), status.isOrAfter(ChunkStatus.BIOMES),
                chunkTag.getBoolean(ChunkSerializer.IS_LIGHT_ON_TAG), upgraded);
    }

    private static PalettedContainer<BlockState> blockStates(CompoundTag sectionTag, ChunkPos chunkPos, int sectionY) {
        if (!sectionTag.contains(BLOCK_STATES_TAG, Tag.TAG_COMPOUND)) {
            return new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(),
                    PalettedContainer.Strategy.SECTION_STATES);
        }

        return ChunkSerializerAccessor.eminus$blockStateCodec()
                .parse(NbtOps.INSTANCE, sectionTag.getCompound(BLOCK_STATES_TAG))
                .promotePartial(message -> ChunkSerializerAccessor.eminus$logErrors(chunkPos, sectionY, message))
                .getOrThrow(ChunkSerializer.ChunkReadException::new);
    }

    private static PalettedContainerRO<Holder<Biome>> biomes(CompoundTag sectionTag, ChunkPos chunkPos, int sectionY,
            Registry<Biome> biomes, Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec) {
        if (!sectionTag.contains(BIOMES_TAG, Tag.TAG_COMPOUND)) {
            return new PalettedContainer<>(biomes.asHolderIdMap(), biomes.getHolderOrThrow(Biomes.PLAINS),
                    PalettedContainer.Strategy.SECTION_BIOMES);
        }

        return biomeCodec.parse(NbtOps.INSTANCE, sectionTag.getCompound(BIOMES_TAG))
                .promotePartial(message -> ChunkSerializerAccessor.eminus$logErrors(chunkPos, sectionY, message))
                .getOrThrow(ChunkSerializer.ChunkReadException::new);
    }

    private static @Nullable DataLayer layer(CompoundTag sectionTag, String key) {
        return sectionTag.contains(key, Tag.TAG_BYTE_ARRAY) ? new DataLayer(sectionTag.getByteArray(key)) : null;
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

    public boolean holdsBlocks(int sectionY) {
        int index = sectionY - minSectionY;
        if (index < 0 || index >= sections.length) {
            return false;
        }

        return occupied.get(index);
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
        PalettedContainerRO<Holder<Biome>> section = biomes[QuartPos.toSection(clampedQuartY) - minSectionY];
        return section == null ? null : section.get(quartX & QUART_IN_SECTION_MASK,
                clampedQuartY & QUART_IN_SECTION_MASK, quartZ & QUART_IN_SECTION_MASK);
    }
}
