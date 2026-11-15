package com.eminus.ingest.saved;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import com.eminus.VanillaBootstrap;
import com.eminus.ingest.SectionConverter;
import com.eminus.ingest.SectionPyramid;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SavedColumnTest {
    private static final int SIDE = SectionPyramid.SECTION_SIDE;
    private static final int SECTIONS = 2;
    private static final int LIGHT_SECTIONS = SECTIONS + 2;
    private static final int LOWER = 0;
    private static final int UPPER = 1;
    private static final int LIGHT_INDEX_OFFSET = 1;
    private static final int SHADE = 7;
    private static final long SEED = 0x5EED_1234_ABCDL;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void anAirSectionAmongAirReadsTheBottomRowOfTheLayerAbove() {
        DataLayer above = shadedBottomRow();
        DataLayer[] sky = new DataLayer[LIGHT_SECTIONS];
        sky[UPPER + LIGHT_INDEX_OFFSET] = above;
        SavedColumn column = column(chunk(air(), air(), sky), true);

        DataLayer inherited = column.skyLight(LOWER);

        assertEquals(SHADE, inherited.get(0, 0, 0));
        assertEquals(SHADE, inherited.get(SIDE - 1, SIDE - 1, SIDE - 1));
        assertSame(above, column.skyLight(UPPER));
    }

    @Test
    void aSectionHoldingOrTouchingBlocksWithNoSavedLayerReadsZero() {
        DataLayer[] sky = new DataLayer[LIGHT_SECTIONS];
        sky[UPPER + LIGHT_INDEX_OFFSET] = shadedBottomRow();
        SavedColumn holding = column(chunk(stone(), air(), sky), true);
        SavedColumn touching = column(chunk(air(), stone(), new DataLayer[LIGHT_SECTIONS]), true);

        assertEquals(0, holding.skyLight(LOWER).get(0, 0, 0));
        assertEquals(0, holding.skyLight(LOWER).get(SIDE - 1, SIDE - 1, SIDE - 1));
        assertEquals(0, touching.skyLight(LOWER).get(0, 0, 0));
        assertTrue(touching.submits(LOWER));
    }

    @Test
    void aSectionWithNoLayerAboveReadsOpenSkyAndDarknessWithoutASky() {
        DataLayer[] none = new DataLayer[LIGHT_SECTIONS];

        assertEquals(SectionConverter.DEFAULT_SKY_LIGHT,
                column(chunk(air(), air(), none), true).skyLight(LOWER).get(0, 0, 0));
        assertEquals(0, column(chunk(air(), air(), none), false).skyLight(LOWER).get(0, 0, 0));
        assertFalse(column(chunk(stone(), air(), none), false).submits(UPPER));
    }

    @Test
    void anAirSectionIsSubmittedOnlyWhereItsLightDiffersFromBlank() {
        DataLayer[] sky = new DataLayer[LIGHT_SECTIONS];
        sky[UPPER + LIGHT_INDEX_OFFSET] = new DataLayer(SHADE);
        SavedColumn shaded = column(chunk(stone(), air(), sky), true);
        SavedColumn open = column(chunk(air(), air(), new DataLayer[LIGHT_SECTIONS]), true);

        assertTrue(shaded.submits(UPPER));
        assertTrue(shaded.submits(LOWER));
        assertFalse(open.submits(UPPER));
        assertFalse(open.submits(LOWER));
    }

    private static DataLayer shadedBottomRow() {
        DataLayer layer = new DataLayer(SectionConverter.DEFAULT_SKY_LIGHT);
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                layer.set(x, 0, z, SHADE);
            }
        }

        return layer;
    }

    @Test
    void aSectionTheSaveLacksIsNotSubmitted() {
        SavedColumn column = column(chunk(stone(), null, new DataLayer[LIGHT_SECTIONS]), true);

        assertFalse(column.submits(UPPER));
    }

    @Test
    void aBiomeAboveTheTopSectionIsReadFromTheTopSectionAndAMissingSectionGivesNone() {
        Holder<Biome> low = Holder.direct(null);
        Holder<Biome> high = Holder.direct(null);
        SavedChunk whole = chunk(section(Blocks.STONE.defaultBlockState(), low),
                section(Blocks.AIR.defaultBlockState(), high), new DataLayer[LIGHT_SECTIONS]);
        SavedChunk holed = chunk(section(Blocks.STONE.defaultBlockState(), low), null,
                new DataLayer[LIGHT_SECTIONS]);
        int aboveTop = QuartPos.fromSection(SECTIONS) + 1;

        assertSame(high, whole.noiseBiome(0, aboveTop, 0));
        assertSame(low, whole.noiseBiome(0, -1, 0));
        assertNull(holed.noiseBiome(0, aboveTop, 0));
    }

    @Test
    void aColumnIsAcceptedOnlyFullLitAndAmongNeighborsHoldingBiomes() {
        SavedChunk full = flagged(true, true, true);
        SavedChunk[] whole = neighborhood(full, full);
        SavedChunk[] lone = neighborhood(full, null);

        assertEquals(SavedColumn.Verdict.ACCEPTED, SavedColumn.judge(whole));
        assertEquals(SavedColumn.Verdict.ACCEPTED, SavedColumn.judge(neighborhood(full, flagged(false, true, false))));
        assertEquals(SavedColumn.Verdict.NOT_SAVED, SavedColumn.judge(neighborhood(null, full)));
        assertEquals(SavedColumn.Verdict.NOT_FULL, SavedColumn.judge(neighborhood(flagged(false, true, true), full)));
        assertEquals(SavedColumn.Verdict.UNLIT, SavedColumn.judge(neighborhood(flagged(true, true, false), full)));
        assertEquals(SavedColumn.Verdict.NO_NEIGHBORS, SavedColumn.judge(lone));
        assertEquals(SavedColumn.Verdict.NO_NEIGHBORS,
                SavedColumn.judge(neighborhood(full, flagged(false, false, false))));
    }

    private static SavedChunk[] neighborhood(@Nullable SavedChunk center, @Nullable SavedChunk neighbor) {
        SavedChunk[] around = new SavedChunk[SavedColumn.NEIGHBOR_SIDE * SavedColumn.NEIGHBOR_SIDE];
        Arrays.fill(around, neighbor);
        around[SavedColumn.neighborIndex(0, 0)] = center;
        return around;
    }

    private static SavedChunk flagged(boolean full, boolean hasBiomes, boolean lit) {
        return new SavedChunk(0, 0, 0, new LevelChunkSection[SECTIONS], new DataLayer[LIGHT_SECTIONS],
                new DataLayer[LIGHT_SECTIONS], full, hasBiomes, lit, false);
    }

    private static SavedColumn column(SavedChunk center, boolean hasSky) {
        SavedChunk[] around = new SavedChunk[SavedColumn.NEIGHBOR_SIDE * SavedColumn.NEIGHBOR_SIDE];
        around[SavedColumn.neighborIndex(0, 0)] = center;
        return new SavedColumn(center, around, hasSky, SEED);
    }

    private static SavedChunk chunk(@Nullable LevelChunkSection lower, @Nullable LevelChunkSection upper,
            DataLayer[] sky) {
        return new SavedChunk(0, 0, 0, new LevelChunkSection[] {lower, upper}, sky, new DataLayer[LIGHT_SECTIONS],
                true, true, true, false);
    }

    private static LevelChunkSection stone() {
        return section(Blocks.STONE.defaultBlockState(), Holder.direct(null));
    }

    private static LevelChunkSection air() {
        return section(Blocks.AIR.defaultBlockState(), Holder.direct(null));
    }

    private static LevelChunkSection section(BlockState state, Holder<Biome> biome) {
        CrudeIncrementalIntIdentityHashBiMap<Holder<Biome>> biomeIds = CrudeIncrementalIntIdentityHashBiMap.create(1);
        biomeIds.add(biome);
        return new LevelChunkSection(
                new PalettedContainer<>(state, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),
                new PalettedContainer<>(biome, Strategy.createForBiomes(biomeIds)));
    }
}
