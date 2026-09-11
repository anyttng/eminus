package com.eminus.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.settings.FogMode;

import org.junit.jupiter.api.Test;

class CompositeFogTest {
    private static final float OVERWORLD_FOG_START = 0.0F;
    private static final float OVERWORLD_FOG_END = 1024.0F;
    private static final float NETHER_FOG_START = 10.0F;
    private static final float NETHER_FOG_END = 96.0F;
    private static final float NEAR_12_CHUNKS = 192.0F;
    private static final float NEAR_2_CHUNKS = 32.0F;
    private static final int FAR_CELLS = 16;
    private static final float FAR_BLOCKS = 8192.0F;
    private static final float TOLERANCE = 1.0E-4F;

    @Test
    void fogReachesFullAtTheFarRenderDistanceAndTheGameValueAtTheNearEdge() {
        CompositeFog fog = CompositeFog.of(FogMode.FOG_AND_FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END,
                NEAR_12_CHUNKS, FAR_CELLS);

        assertEquals(FAR_BLOCKS, fog.fogEnd());
        assertEquals(NEAR_12_CHUNKS / OVERWORLD_FOG_END, valueAt(fog, NEAR_12_CHUNKS), TOLERANCE);
        assertEquals(1.0F, valueAt(fog, FAR_BLOCKS), TOLERANCE);
    }

    @Test
    void aGameFogStartAboveZeroKeepsItsValueAtTheNearEdge() {
        CompositeFog fog = CompositeFog.of(FogMode.FOG, NETHER_FOG_START, NETHER_FOG_END, NEAR_2_CHUNKS, FAR_CELLS);

        float atNear = (NEAR_2_CHUNKS - NETHER_FOG_START) / (NETHER_FOG_END - NETHER_FOG_START);
        assertEquals(atNear, valueAt(fog, NEAR_2_CHUNKS), TOLERANCE);
        assertEquals(FAR_BLOCKS, fog.fogEnd());
        assertFalse(fog.skip());
    }

    @Test
    void fogAndFadeCarriesTheOuterBand() {
        CompositeFog fog = CompositeFog.of(FogMode.FOG_AND_FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END,
                NEAR_12_CHUNKS, FAR_CELLS);

        assertEquals(FAR_BLOCKS - CompositeFog.FADE_BAND_BLOCKS, fog.fadeStart());
        assertEquals(FAR_BLOCKS, fog.fadeEnd());
    }

    @Test
    void fogAloneLeavesTheFadeOff() {
        CompositeFog fog = CompositeFog.of(FogMode.FOG, OVERWORLD_FOG_START, OVERWORLD_FOG_END, NEAR_12_CHUNKS,
                FAR_CELLS);

        assertEquals(CompositeFog.NONE, fog.fadeStart());
        assertEquals(CompositeFog.NONE, fog.fadeEnd());
    }

    @Test
    void fadeAloneLeavesTheFogOff() {
        CompositeFog fog = CompositeFog.of(FogMode.FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END, NEAR_12_CHUNKS,
                FAR_CELLS);

        assertEquals(CompositeFog.NONE, fog.fogStart());
        assertEquals(CompositeFog.NONE, fog.fogEnd());
        assertEquals(FAR_BLOCKS, fog.fadeEnd());
    }

    @Test
    void offLeavesBothOffAndNeverSkips() {
        CompositeFog fog = CompositeFog.of(FogMode.OFF, NETHER_FOG_START, NETHER_FOG_END, NEAR_12_CHUNKS, FAR_CELLS);

        assertEquals(CompositeFog.NONE, fog.fogStart());
        assertEquals(CompositeFog.NONE, fog.fogEnd());
        assertEquals(CompositeFog.NONE, fog.fadeStart());
        assertEquals(CompositeFog.NONE, fog.fadeEnd());
        assertFalse(fog.skip());
    }

    @Test
    void aFogEndAtOrNearerThanTheRenderDistanceSkips() {
        assertTrue(CompositeFog.of(FogMode.FOG_AND_FADE, NETHER_FOG_START, NETHER_FOG_END, NEAR_12_CHUNKS, FAR_CELLS)
                .skip());
        assertTrue(CompositeFog.of(FogMode.FOG, NETHER_FOG_START, NEAR_12_CHUNKS, NEAR_12_CHUNKS, FAR_CELLS).skip());
    }

    @Test
    void aFogEndBeyondTheRenderDistanceDoesNotSkip() {
        assertFalse(CompositeFog.of(FogMode.FOG_AND_FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END, NEAR_12_CHUNKS,
                FAR_CELLS).skip());
    }

    @Test
    void fadeAloneNeverSkipsEvenWithANearFogEnd() {
        assertFalse(CompositeFog.of(FogMode.FADE, NETHER_FOG_START, NETHER_FOG_END, NEAR_12_CHUNKS, FAR_CELLS).skip());
    }

    @Test
    void aFarDistanceInsideTheNearFieldKeepsTheGameFog() {
        CompositeFog fog = CompositeFog.of(FogMode.FOG, OVERWORLD_FOG_START, OVERWORLD_FOG_END, 1024.0F, 1);

        assertEquals(OVERWORLD_FOG_START, fog.fogStart());
        assertEquals(OVERWORLD_FOG_END, fog.fogEnd());
    }

    @Test
    void oneCellOfFarDistanceFadesFromTheCamera() {
        CompositeFog fog = CompositeFog.of(FogMode.FADE, OVERWORLD_FOG_START, OVERWORLD_FOG_END, NEAR_12_CHUNKS, 1);

        assertEquals(0.0F, fog.fadeStart());
        assertEquals(CompositeFog.FADE_BAND_BLOCKS, fog.fadeEnd());
    }

    private static float valueAt(CompositeFog fog, float distance) {
        return (distance - fog.fogStart()) / (fog.fogEnd() - fog.fogStart());
    }
}
