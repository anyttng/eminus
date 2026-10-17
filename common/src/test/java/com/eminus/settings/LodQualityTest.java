package com.eminus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LodQualityTest {
    @Test
    void ultraAloneFollowsTheOption() {
        assertTrue(LodQuality.ULTRA.cutoutLeaves(true));
        assertFalse(LodQuality.ULTRA.cutoutLeaves(false));
    }

    @Test
    void everyOtherPresetDrawsLeavesOpaqueWhateverTheOptionSays() {
        for (LodQuality quality : LodQuality.values()) {
            if (quality != LodQuality.ULTRA) {
                assertFalse(quality.cutoutLeaves(true), quality.key());
                assertFalse(quality.cutoutLeaves(false), quality.key());
            }
        }
    }

    @Test
    void anOldStoredLevelMapsToTheFirstPresetStoringIt() {
        assertEquals(LodQuality.ULTRA, LodQuality.fromLowestStoredLevel(0));
        assertEquals(LodQuality.MEDIUM, LodQuality.fromLowestStoredLevel(1));
        assertEquals(LodQuality.LOW, LodQuality.fromLowestStoredLevel(2));
        assertEquals(LodQuality.MINIMAL, LodQuality.fromLowestStoredLevel(3));
        assertEquals(LodQuality.MINIMAL, LodQuality.fromLowestStoredLevel(4));
    }

    @Test
    void ultraAloneDrawsBlades() {
        for (LodQuality quality : LodQuality.values()) {
            assertEquals(quality == LodQuality.ULTRA, quality.drawsBlades(), quality.key());
        }
    }

    @Test
    void lowAndMinimalAloneUploadTheAtlasFromTheFirstMip() {
        assertEquals(0, LodQuality.ULTRA.atlasFirstMip());
        assertEquals(0, LodQuality.HIGH.atlasFirstMip());
        assertEquals(0, LodQuality.MEDIUM.atlasFirstMip());
        assertEquals(1, LodQuality.LOW.atlasFirstMip());
        assertEquals(1, LodQuality.MINIMAL.atlasFirstMip());
    }
}
