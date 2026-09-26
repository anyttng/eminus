package com.eminus.client.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.handoff.NearFieldOverride;

import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.junit.jupiter.api.Test;

class GameFramesTest {
    private static final boolean CLEAR_ATMOSPHERIC_FOG = true;
    private static final boolean KEEP_ATMOSPHERIC_FOG = false;
    private static final boolean RENDER_DISTANCE_FOG = true;
    private static final boolean ENVIRONMENTAL_FOG = false;
    private static final float FOG_START = 128.0F;
    private static final float FOG_END = 192.0F;
    private static final Vector4fc COLOUR = new Vector4f(0.5F, 0.6F, 0.7F, 1.0F);
    private static final double FADE_IN_SECONDS = 0.5;

    @Test
    void theRenderDistanceBranchFillsTheRenderDistancePairAlone() {
        GameFog fog = GameFrames.fog(FOG_START, FOG_END, RENDER_DISTANCE_FOG, COLOUR);

        assertEquals(new GameFog(GameFrames.NO_FOG, GameFrames.NO_FOG, FOG_START, FOG_END, COLOUR), fog);
    }

    @Test
    void everyOtherBranchFillsTheEnvironmentalPairAlone() {
        GameFog fog = GameFrames.fog(FOG_START, FOG_END, ENVIRONMENTAL_FOG, COLOUR);

        assertEquals(new GameFog(FOG_START, FOG_END, GameFrames.NO_FOG, GameFrames.NO_FOG, COLOUR), fog);
    }

    @Test
    void theRenderDistanceFogIsClearedWhateverTheAtmosphericSetting() {
        assertTrue(GameFrames.clears(RENDER_DISTANCE_FOG, KEEP_ATMOSPHERIC_FOG));
        assertTrue(GameFrames.clears(RENDER_DISTANCE_FOG, CLEAR_ATMOSPHERIC_FOG));
    }

    @Test
    void environmentalFogIsClearedOnlyWhenAtmosphericFogIsCleared() {
        assertFalse(GameFrames.clears(ENVIRONMENTAL_FOG, KEEP_ATMOSPHERIC_FOG));
        assertTrue(GameFrames.clears(ENVIRONMENTAL_FOG, CLEAR_ATMOSPHERIC_FOG));
    }

    @Test
    void anAppliedOverrideTakesTheFadeInToZero() {
        NearFieldOverride.apply();

        assertEquals(GameFrames.NO_FADE_IN, GameFrames.sectionFadeInSeconds(FADE_IN_SECONDS));
    }

    @Test
    void aSkippedOverrideLeavesTheFadeInAtTheOption() {
        NearFieldOverride.apply();

        NearFieldOverride.skip();

        assertEquals(FADE_IN_SECONDS, GameFrames.sectionFadeInSeconds(FADE_IN_SECONDS));
    }
}
