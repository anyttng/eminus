package com.eminus.ingest;

import com.eminus.ingest.saved.SavedChunk;
import com.eminus.ingest.saved.SavedColumn;

import net.minecraft.core.Holder;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

public final class IngestColumns {
    public static final int BOTTOM = 0;
    public static final int TOP = 2;

    private static final int SECTIONS = 3;
    private static final int LIGHT_SECTIONS = SECTIONS + 2;

    public static SavedChunk stoneChunk(int chunkX, int chunkZ, int minSectionY) {
        LevelChunkSection[] sections = new LevelChunkSection[SECTIONS];
        sections[BOTTOM] = section(Blocks.STONE.defaultBlockState());
        sections[TOP] = section(Blocks.STONE.defaultBlockState());
        return new SavedChunk(chunkX, chunkZ, minSectionY, sections, new DataLayer[LIGHT_SECTIONS],
                new DataLayer[LIGHT_SECTIONS], true, true, true, false);
    }

    public static SavedColumn stone(int chunkX, int chunkZ, int minSectionY, long biomeZoomSeed) {
        SavedChunk center = stoneChunk(chunkX, chunkZ, minSectionY);
        SavedChunk[] around = new SavedChunk[SavedColumn.NEIGHBOR_SIDE * SavedColumn.NEIGHBOR_SIDE];
        around[SavedColumn.neighborIndex(0, 0)] = center;
        return new SavedColumn(center, around, false, biomeZoomSeed);
    }

    public static void submit(IngestService ingest, IngestColumn column) {
        ingest.ingestSections(column, IngestService.pickSections(column), IngestTrigger.PACKET);
    }

    private static LevelChunkSection section(BlockState state) {
        Holder<Biome> biome = Holder.direct(null);
        CrudeIncrementalIntIdentityHashBiMap<Holder<Biome>> biomeIds = CrudeIncrementalIntIdentityHashBiMap.create(1);
        biomeIds.add(biome);
        return new LevelChunkSection(
                new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, state, PalettedContainer.Strategy.SECTION_STATES),
                new PalettedContainer<>(biomeIds, biome, PalettedContainer.Strategy.SECTION_BIOMES));
    }

    private IngestColumns() {
    }
}
