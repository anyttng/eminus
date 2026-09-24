package com.eminus.render.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.cell.DetailLevel;
import com.eminus.settings.DetailDistance;
import com.eminus.settings.FarDistance;
import com.eminus.settings.Settings;

import org.junit.jupiter.api.Test;

class ArenaSizingTest {
    private static final long ROOMY_DEVICE = 8L * 1024 * 1024 * 1024;
    private static final long CRAMPED_DEVICE = 64L * 1024 * 1024;
    private static final float FOCAL_PIXELS = 978.0F;
    private static final int FAR_CELLS = Settings.DEFAULT_FAR_RENDER_CELLS;
    private static final int SUBDIVISION = Settings.DEFAULT_DETAIL_DISTANCE.pixels();
    private static final int LOWEST_LEVEL = Settings.DEFAULT_LOWEST_STORED_LEVEL;
    private static final float MEASURED_FOCAL_PIXELS = 977.6F;
    private static final int MEASURED_FAR_CHUNKS = 128;
    private static final int MEASURED_FAR_CELLS = FarDistance.chunksToCells(MEASURED_FAR_CHUNKS);
    private static final long WHOLE_PERCENT = 100L;
    private static final long HIGH_WATER_PERCENT = 85L;
    private static final long FILL_PERCENT = 95L;

    @Test
    void aBudgetBelowTheFloorIsLiftedToIt() {
        assertEquals(ArenaSizing.MIN_BYTES, ArenaSizing.wanted(Settings.MIN_FAR_RENDER_CELLS,
                DetailDistance.MINIMAL.pixels(), FOCAL_PIXELS, DetailLevel.MAX));
    }

    @Test
    void theSmallestRenderDistanceStillPaysForTheFinestShell() {
        long bytes = ArenaSizing.wanted(Settings.MIN_FAR_RENDER_CELLS, SUBDIVISION, FOCAL_PIXELS, LOWEST_LEVEL);

        assertTrue(bytes > ArenaSizing.MIN_BYTES,
                "The level-0 shell reaches past the shortest far distance, so the floor does not bind: " + bytes);
    }

    @Test
    void theLargestRenderDistanceIsCappedAtTheMaximum() {
        assertEquals(ArenaSizing.MAX_BYTES,
                ArenaSizing.wanted(Settings.MAX_FAR_RENDER_CELLS, SUBDIVISION, FOCAL_PIXELS, LOWEST_LEVEL));
    }

    @Test
    void theDefaultSettingsLandBetweenTheBounds() {
        long bytes = ArenaSizing.wanted(FAR_CELLS, SUBDIVISION, FOCAL_PIXELS, LOWEST_LEVEL);

        assertTrue(bytes > ArenaSizing.MIN_BYTES, "The default asks for more than the minimum: " + bytes);
        assertTrue(bytes < ArenaSizing.MAX_BYTES, "The default asks for less than the maximum: " + bytes);
    }

    @Test
    void theDemandAtLowIsPinned() {
        long quads = 733L * 3 * 6600 + 733L * 2 * 9500 + 733L * 11400 + 201L * 7200 + 50L * 5700;

        assertEquals(withHeadroom(quads), ArenaSizing.wanted(MEASURED_FAR_CELLS, DetailDistance.LOW.pixels(),
                MEASURED_FOCAL_PIXELS, DetailLevel.MIN));
    }

    @Test
    void theDemandAtMediumIsPinned() {
        long quads = 2932L * 3 * 6600 + 2932L * 2 * 9500 + 804L * 11400 + 201L * 7200 + 50L * 5700;

        assertEquals(withHeadroom(quads), ArenaSizing.wanted(MEASURED_FAR_CELLS, DetailDistance.MEDIUM.pixels(),
                MEASURED_FOCAL_PIXELS, DetailLevel.MIN));
    }

    private static long withHeadroom(long quads) {
        return Math.ceilDiv(quads * Long.BYTES * WHOLE_PERCENT * WHOLE_PERCENT, HIGH_WATER_PERCENT * FILL_PERCENT);
    }

    @Test
    void halvingTheSubdivisionSizeAsksForAboutFourTimesAsMuch() {
        long coarse = ArenaSizing.wanted(FAR_CELLS, SUBDIVISION * 2, FOCAL_PIXELS, LOWEST_LEVEL);
        long fine = ArenaSizing.wanted(FAR_CELLS, SUBDIVISION, FOCAL_PIXELS, LOWEST_LEVEL);

        assertTrue(fine > 3 * coarse && fine < 5 * coarse,
                "A twice finer subdivision asks for " + fine + " against " + coarse);
    }

    @Test
    void aTallerWindowAsksForMore() {
        long shortWindow = ArenaSizing.wanted(FAR_CELLS, SUBDIVISION, FOCAL_PIXELS / 2, LOWEST_LEVEL);
        long tallWindow = ArenaSizing.wanted(FAR_CELLS, SUBDIVISION, FOCAL_PIXELS, LOWEST_LEVEL);

        assertTrue(tallWindow > shortWindow,
                "A twice taller window asks for " + tallWindow + " against " + shortWindow);
    }

    @Test
    void aHigherLowestStoredLevelDropsTheFinestLevelFromTheBudget() {
        long whole = ArenaSizing.wanted(FAR_CELLS, SUBDIVISION, FOCAL_PIXELS, DetailLevel.MIN);
        long withoutTheFinest = ArenaSizing.wanted(FAR_CELLS, SUBDIVISION, FOCAL_PIXELS, DetailLevel.MIN + 1);

        assertTrue(whole > withoutTheFinest,
                "Storing level 0 asks for " + whole + " against " + withoutTheFinest + " without it");
    }

    @Test
    void aRoomyDeviceGetsWhatWasWantedDownToTheBlock() {
        long wanted = ArenaSizing.wanted(FAR_CELLS, SUBDIVISION, FOCAL_PIXELS, LOWEST_LEVEL);

        assertEquals(wanted - wanted % ArenaSizing.BLOCK_BYTES, ArenaSizing.fitted(wanted, ROOMY_DEVICE));
    }

    @Test
    void aDeviceShareBelowTheMinimumRefusesTheArena() {
        assertEquals(ArenaSizing.REFUSED, ArenaSizing.fitted(ArenaSizing.MAX_BYTES, CRAMPED_DEVICE));
    }

    @Test
    void theFittedSizeIsAWholeNumberOfBlocks() {
        long unaligned = ArenaSizing.MIN_BYTES + ArenaSizing.BLOCK_BYTES + 1L;
        long fitted = ArenaSizing.fitted(unaligned, ROOMY_DEVICE);

        assertEquals(0L, fitted % ArenaSizing.BLOCK_BYTES);
        assertEquals(fitted / ArenaSizing.BLOCK_BYTES, ArenaSizing.blocks(fitted));
    }
}
