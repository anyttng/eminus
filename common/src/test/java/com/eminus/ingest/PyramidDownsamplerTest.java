package com.eminus.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.VoxelEntry;

import org.junit.jupiter.api.Test;

class PyramidDownsamplerTest {
    private static final int FLOWER = 1;
    private static final int STONE = 2;
    private static final int TRUNK = 3;
    private static final int LEAVES = 4;
    private static final int WATER = 5;
    private static final int SNOW = 6;

    private static final int[] OPACITY = {0, 0, 15, 15, 15, 1, 0};
    private static final StateOpacity OPACITIES = new StateOpacity() {
        @Override
        public int opacity(int stateId) {
            return OPACITY[stateId];
        }

        @Override
        public boolean cover(int stateId) {
            return stateId == SNOW;
        }
    };

    private static final int BIOME = 7;
    private static final int TOP_BIOME = 9;
    private static final int HALF_SECTION = SectionPyramid.SECTION_SIDE / 2;
    private static final int SNOW_Y = 3;

    private final SectionPyramid pyramid = new SectionPyramid();

    @Test
    void theHighestOpacityWinsTheGroup() {
        fill(entry(FLOWER));
        set(0, 0, 0, entry(STONE));

        PyramidDownsampler.build(pyramid, OPACITIES);

        assertEquals(STONE, VoxelEntry.state(at(DetailLevel.MIN + 1, 0, 0, 0)));
    }

    @Test
    void aTieGoesToTheCornerNearestTheTop() {
        fill(VoxelEntry.AIR);
        set(0, 0, 0, entry(TRUNK));
        set(1, 1, 1, entry(LEAVES));

        PyramidDownsampler.build(pyramid, OPACITIES);

        assertEquals(LEAVES, VoxelEntry.state(at(DetailLevel.MIN + 1, 0, 0, 0)));
    }

    @Test
    void anAllAirGroupKeepsTheAveragedLight() {
        fill(VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, BIOME, VoxelEntry.light(4, 2)));
        set(1, 1, 1, VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, TOP_BIOME, VoxelEntry.light(12, 10)));

        PyramidDownsampler.build(pyramid, OPACITIES);

        long merged = at(DetailLevel.MIN + 1, 0, 0, 0);
        assertTrue(VoxelEntry.isAir(merged));
        assertEquals(TOP_BIOME, VoxelEntry.biome(merged));
        assertEquals(5, VoxelEntry.skyLight(merged));
        assertEquals(3, VoxelEntry.blockLight(merged));
    }

    @Test
    void leavesOverATrunkSurviveToTheTopLevel() {
        fill(VoxelEntry.AIR);
        int corner = SectionPyramid.SECTION_SIDE - 1;

        for (int y = 0; y < HALF_SECTION; y++) {
            set(corner, y, corner, entry(TRUNK));
        }

        for (int y = HALF_SECTION; y < SectionPyramid.SECTION_SIDE; y++) {
            set(corner, y, corner, entry(LEAVES));
        }

        PyramidDownsampler.build(pyramid, OPACITIES);

        assertEquals(LEAVES, VoxelEntry.state(at(DetailLevel.MAX, 0, 0, 0)));
    }

    @Test
    void aCoarseVoxelKeepsTheHeightOfItsHighestBlock() {
        fill(VoxelEntry.AIR);
        for (int y = 0; y < 3; y++) {
            set(0, y, 0, entry(STONE));
        }

        PyramidDownsampler.build(pyramid, OPACITIES);

        long coarsest = at(DetailLevel.MAX, 0, 0, 0);
        assertEquals(0, VoxelEntry.lowGap(coarsest));
        assertEquals(SectionPyramid.SECTION_SIDE - 3, VoxelEntry.highGap(coarsest));
        assertEquals(1, VoxelEntry.highGap(at(DetailLevel.MIN + 1, 0, 1, 0)));
    }

    @Test
    void aFloatingBlockKeepsTheGapBelowIt() {
        fill(VoxelEntry.AIR);
        set(3, 5, 3, entry(STONE));

        PyramidDownsampler.build(pyramid, OPACITIES);

        long coarsest = at(DetailLevel.MAX, 0, 0, 0);
        assertEquals(5, VoxelEntry.lowGap(coarsest));
        assertEquals(SectionPyramid.SECTION_SIDE - 6, VoxelEntry.highGap(coarsest));
    }

    @Test
    void aFlowerAboveTheGroundDoesNotLiftTheTop() {
        fill(VoxelEntry.AIR);
        for (int z = 0; z < SectionPyramid.SECTION_SIDE; z++) {
            for (int x = 0; x < SectionPyramid.SECTION_SIDE; x++) {
                set(x, 0, z, entry(STONE));
                set(x, 1, z, entry(FLOWER));
            }
        }

        PyramidDownsampler.build(pyramid, OPACITIES);

        long coarsest = at(DetailLevel.MAX, 0, 0, 0);
        assertEquals(STONE, VoxelEntry.state(coarsest));
        assertEquals(SectionPyramid.SECTION_SIDE - 1, VoxelEntry.highGap(coarsest));
    }

    @Test
    void waterAboveTheSeabedLiftsTheTopToItsSurface() {
        fill(VoxelEntry.AIR);
        for (int z = 0; z < SectionPyramid.SECTION_SIDE; z++) {
            for (int x = 0; x < SectionPyramid.SECTION_SIDE; x++) {
                set(x, 0, z, entry(STONE));
                for (int y = 1; y < 7; y++) {
                    set(x, y, z, entry(WATER));
                }
            }
        }

        PyramidDownsampler.build(pyramid, OPACITIES);

        long coarsest = at(DetailLevel.MAX, 0, 0, 0);
        assertEquals(STONE, VoxelEntry.state(coarsest));
        assertEquals(0, VoxelEntry.lowGap(coarsest));
        assertEquals(SectionPyramid.SECTION_SIDE - 7, VoxelEntry.highGap(coarsest));
    }

    @Test
    void aGroupOfFlowersAloneKeepsTheFlowersHeight() {
        fill(VoxelEntry.AIR);
        set(2, 7, 2, entry(FLOWER));

        PyramidDownsampler.build(pyramid, OPACITIES);

        long coarsest = at(DetailLevel.MAX, 0, 0, 0);
        assertEquals(FLOWER, VoxelEntry.state(coarsest));
        assertEquals(7, VoxelEntry.lowGap(coarsest));
        assertEquals(SectionPyramid.SECTION_SIDE - 8, VoxelEntry.highGap(coarsest));
    }

    @Test
    void snowOnTheGroundWinsEveryLevelAtTheGroundsHeight() {
        fill(VoxelEntry.AIR);
        for (int z = 0; z < SectionPyramid.SECTION_SIDE; z++) {
            for (int x = 0; x < SectionPyramid.SECTION_SIDE; x++) {
                for (int y = 0; y < SNOW_Y; y++) {
                    set(x, y, z, entry(STONE));
                }
                set(x, SNOW_Y, z, entry(SNOW));
            }
        }

        PyramidDownsampler.build(pyramid, OPACITIES);

        long first = at(DetailLevel.MIN + 1, 0, 1, 0);
        assertEquals(SNOW, VoxelEntry.state(first));
        assertEquals(VoxelEntry.NO_GAPS, VoxelEntry.gaps(first));
        assertEquals(STONE, VoxelEntry.state(at(DetailLevel.MIN + 1, 0, 0, 0)));
        for (int level = DetailLevel.MIN + 2; level <= DetailLevel.MAX; level++) {
            long coarse = at(level, 0, 0, 0);
            assertEquals(SNOW, VoxelEntry.state(coarse));
            assertEquals(0, VoxelEntry.lowGap(coarse));
            assertEquals(DetailLevel.blocksPerVoxel(level) - SNOW_Y - 1, VoxelEntry.highGap(coarse));
        }
    }

    @Test
    void snowBelowAHigherColumnLosesItsGroup() {
        fill(VoxelEntry.AIR);
        for (int z = 0; z < 2; z++) {
            for (int x = 0; x < 2; x++) {
                set(x, 0, z, entry(STONE));
                set(x, 1, z, entry(SNOW));
            }
        }
        set(1, 1, 1, entry(STONE));

        PyramidDownsampler.build(pyramid, OPACITIES);

        assertEquals(STONE, VoxelEntry.state(at(DetailLevel.MIN + 1, 0, 0, 0)));
    }

    @Test
    void snowWinningAGroupWithoutGroundKeepsItsOwnBlock() {
        fill(VoxelEntry.AIR);
        set(0, 0, 0, entry(FLOWER));
        set(1, 1, 1, entry(SNOW));

        PyramidDownsampler.build(pyramid, OPACITIES);

        long merged = at(DetailLevel.MIN + 1, 0, 0, 0);
        assertEquals(SNOW, VoxelEntry.state(merged));
        assertEquals(1, VoxelEntry.lowGap(merged));
        assertEquals(0, VoxelEntry.highGap(merged));
    }

    @Test
    void aVoxelWithGapsCarriesTheLightOfItsEmptyPart() {
        fill(VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, BIOME, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 6)));
        for (int z = 0; z < SectionPyramid.SECTION_SIDE; z++) {
            for (int x = 0; x < SectionPyramid.SECTION_SIDE; x++) {
                set(x, 0, z, VoxelEntry.pack(STONE, BIOME, VoxelEntry.light(0, 0)));
            }
        }

        PyramidDownsampler.build(pyramid, OPACITIES);

        for (int level = DetailLevel.MIN + 1; level <= DetailLevel.MAX; level++) {
            long ground = at(level, 0, 0, 0);
            assertEquals(STONE, VoxelEntry.state(ground));
            assertEquals(VoxelEntry.MAX_LIGHT, VoxelEntry.skyLight(ground));
            assertEquals(6, VoxelEntry.blockLight(ground));
        }
    }

    @Test
    void aFullVoxelKeepsItsWinnersLight() {
        fill(VoxelEntry.pack(STONE, BIOME, VoxelEntry.light(0, 0)));

        PyramidDownsampler.build(pyramid, OPACITIES);

        assertEquals(0, VoxelEntry.skyLight(at(DetailLevel.MAX, 0, 0, 0)));
    }

    @Test
    void aFullSectionLeavesEveryLevelWithoutGaps() {
        fill(entry(STONE));

        PyramidDownsampler.build(pyramid, OPACITIES);

        for (int level = DetailLevel.MIN + 1; level <= DetailLevel.MAX; level++) {
            assertEquals(VoxelEntry.NO_GAPS, VoxelEntry.gaps(at(level, 0, 0, 0)));
        }
    }

    private void fill(long entry) {
        Arrays.fill(pyramid.level(DetailLevel.MIN), entry);
    }

    private void set(int x, int y, int z, long entry) {
        pyramid.level(DetailLevel.MIN)[SectionPyramid.indexAt(SectionPyramid.SECTION_SIDE, x, y, z)] = entry;
    }

    private long at(int level, int x, int y, int z) {
        return pyramid.level(level)[SectionPyramid.indexAt(SectionPyramid.sideOf(level), x, y, z)];
    }

    private static long entry(int state) {
        return VoxelEntry.pack(state, BIOME, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0));
    }
}
