package com.eminus.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompositeFogTest {
    private static final boolean FOG = true;
    private static final boolean NO_FOG = false;
    private static final boolean FADE = true;
    private static final boolean NO_FADE = false;
    private static final float OVERWORLD_FOG_START = 0.0F;
    private static final float OVERWORLD_FOG_END = 1024.0F;
    private static final float NETHER_FOG_START = 10.0F;
    private static final float NETHER_FOG_END = 96.0F;
    private static final float NEAR_12_CHUNKS = 192.0F;
    private static final float NEAR_2_CHUNKS = 32.0F;
    private static final float CORNER_12_CHUNKS = 294.0F;
    private static final float REACH_12_CHUNKS = 460.0F;
    private static final int FAR_CELLS = 16;
    private static final float FAR_BLOCKS = 8192.0F;
    private static final float TOLERANCE = 1.0E-4F;

    @Test
    void fogReachesFullAtTheFarRenderDistanceAndTheGameValueAtTheReach() {
        CompositeFog fog = CompositeFog.of(FOG, FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END, NEAR_12_CHUNKS,
                REACH_12_CHUNKS, FAR_CELLS);

        assertEquals(FAR_BLOCKS, fog.fogEnd());
        assertEquals(REACH_12_CHUNKS / OVERWORLD_FOG_END, fog.valueAt(REACH_12_CHUNKS), TOLERANCE);
        assertEquals(REACH_12_CHUNKS / OVERWORLD_FOG_END, fog.valueAt(Math.nextUp(REACH_12_CHUNKS)), TOLERANCE);
        assertEquals(1.0F, fog.valueAt(FAR_BLOCKS), TOLERANCE);
    }

    @Test
    void everyDistanceTheNearFieldCanDrawTakesTheGameFog() {
        CompositeFog fog = CompositeFog.of(FOG, FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END, NEAR_12_CHUNKS,
                REACH_12_CHUNKS, FAR_CELLS);

        assertEquals(NEAR_12_CHUNKS / OVERWORLD_FOG_END, fog.valueAt(NEAR_12_CHUNKS), TOLERANCE);
        assertEquals(CORNER_12_CHUNKS / OVERWORLD_FOG_END, fog.valueAt(CORNER_12_CHUNKS), TOLERANCE);
    }

    @Test
    void pastTheReachTheFogClimbsMoreSlowlyThanTheGames() {
        CompositeFog fog = CompositeFog.of(FOG, FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END, NEAR_12_CHUNKS,
                REACH_12_CHUNKS, FAR_CELLS);

        float atReach = REACH_12_CHUNKS / OVERWORLD_FOG_END;
        float expected = atReach + (1.0F - atReach) * (OVERWORLD_FOG_END - REACH_12_CHUNKS)
                / (FAR_BLOCKS - REACH_12_CHUNKS);
        assertEquals(expected, fog.valueAt(OVERWORLD_FOG_END), TOLERANCE);
    }

    @Test
    void aGameFogStartAboveZeroKeepsItsValueAtTheNearEdge() {
        CompositeFog fog = CompositeFog.of(FOG, FADE, NETHER_FOG_START, NETHER_FOG_END,
                NEAR_2_CHUNKS, NEAR_2_CHUNKS, FAR_CELLS);

        float atNear = (NEAR_2_CHUNKS - NETHER_FOG_START) / (NETHER_FOG_END - NETHER_FOG_START);
        assertEquals(atNear, fog.valueAt(NEAR_2_CHUNKS), TOLERANCE);
        assertEquals(FAR_BLOCKS, fog.fogEnd());
        assertFalse(fog.skip());
    }

    @Test
    void theOuterBandFadesWithTheFogOn() {
        CompositeFog fog = CompositeFog.of(FOG, FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS);

        assertEquals(FAR_BLOCKS - CompositeFog.FADE_BAND_BLOCKS, fog.fadeStart());
        assertEquals(FAR_BLOCKS, fog.fadeEnd());
    }

    @Test
    void theFogOffLeavesTheFogOutAndTheOuterBandFading() {
        CompositeFog fog = CompositeFog.of(NO_FOG, FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS);

        assertEquals(CompositeFog.NONE, fog.fogStart());
        assertEquals(CompositeFog.NONE, fog.fogEnd());
        assertEquals(FAR_BLOCKS - CompositeFog.FADE_BAND_BLOCKS, fog.fadeStart());
        assertEquals(FAR_BLOCKS, fog.fadeEnd());
    }

    @Test
    void theFadeOffLeavesTheOuterBandOutAndTheFogOn() {
        CompositeFog fog = CompositeFog.of(FOG, NO_FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS);

        assertEquals(CompositeFog.NONE, fog.fadeStart());
        assertEquals(CompositeFog.NONE, fog.fadeEnd());
        assertEquals(FAR_BLOCKS, fog.fogEnd());
    }

    @Test
    void bothOffLeaveEverythingOutAndStillSkipUnderANearFogEnd() {
        CompositeFog open = CompositeFog.of(NO_FOG, NO_FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS);
        CompositeFog nether = CompositeFog.of(NO_FOG, NO_FADE, NETHER_FOG_START, NETHER_FOG_END,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS);

        assertEquals(CompositeFog.NONE, open.fogStart());
        assertEquals(CompositeFog.NONE, open.fogEnd());
        assertEquals(CompositeFog.NONE, open.fadeStart());
        assertEquals(CompositeFog.NONE, open.fadeEnd());
        assertFalse(open.skip());
        assertTrue(nether.skip());
    }

    @Test
    void aFogEndAtOrNearerThanTheRenderDistanceSkipsWhateverTheFogSetting() {
        assertTrue(CompositeFog.of(FOG, FADE, NETHER_FOG_START, NETHER_FOG_END,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS).skip());
        assertTrue(CompositeFog.of(FOG, FADE, NETHER_FOG_START,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS).skip());
        assertTrue(CompositeFog.of(NO_FOG, FADE, NETHER_FOG_START, NETHER_FOG_END,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS).skip());
    }

    @Test
    void aFogEndBeyondTheRenderDistanceDoesNotSkip() {
        assertFalse(CompositeFog.of(FOG, FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS).skip());
        assertFalse(CompositeFog.of(NO_FOG, FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS).skip());
    }

    @Test
    void aFarDistanceInsideTheNearFieldKeepsTheGameFog() {
        CompositeFog fog = CompositeFog.of(FOG, FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END, 1024.0F, 1024.0F, 1);

        assertEquals(OVERWORLD_FOG_START, fog.fogStart());
        assertEquals(OVERWORLD_FOG_END, fog.fogEnd());
    }

    @Test
    void oneCellOfFarDistanceFadesFromTheCamera() {
        CompositeFog fog = CompositeFog.of(NO_FOG, FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END,
                NEAR_12_CHUNKS, NEAR_12_CHUNKS, 1);

        assertEquals(0.0F, fog.fadeStart());
        assertEquals(CompositeFog.FADE_BAND_BLOCKS, fog.fadeEnd());
    }

    @Test
    void theSkipPredicateAnswersForTheFogEnd() {
        assertTrue(CompositeFog.skipped(NETHER_FOG_END, NEAR_12_CHUNKS));
        assertFalse(CompositeFog.skipped(OVERWORLD_FOG_END, NEAR_12_CHUNKS));
    }
}
