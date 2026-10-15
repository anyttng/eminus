package com.eminus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LodQualityTest {
    @Test
    void fullAloneFollowsTheOption() {
        assertTrue(LodQuality.FULL.cutoutLeaves(true));
        assertFalse(LodQuality.FULL.cutoutLeaves(false));
    }

    @Test
    void everyOtherPresetDrawsLeavesOpaqueWhateverTheOptionSays() {
        for (LodQuality quality : LodQuality.values()) {
            if (quality != LodQuality.FULL) {
                assertFalse(quality.cutoutLeaves(true), quality.key());
                assertFalse(quality.cutoutLeaves(false), quality.key());
            }
        }
    }

    @Test
    void anOldStoredLevelMapsToTheFirstPresetStoringIt() {
        assertEquals(LodQuality.FULL, LodQuality.fromLowestStoredLevel(0));
        assertEquals(LodQuality.MEDIUM, LodQuality.fromLowestStoredLevel(1));
        assertEquals(LodQuality.LOW, LodQuality.fromLowestStoredLevel(2));
        assertEquals(LodQuality.MINIMAL, LodQuality.fromLowestStoredLevel(3));
        assertEquals(LodQuality.MINIMAL, LodQuality.fromLowestStoredLevel(4));
    }

    @Test
    void fullAloneDrawsBlades() {
        for (LodQuality quality : LodQuality.values()) {
            assertEquals(quality == LodQuality.FULL, quality.drawsBlades(), quality.key());
        }
    }
}
