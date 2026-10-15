package com.eminus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LodQualityTest {
    @Test
    void highDrawsLeavesOpaqueWhateverTheOptionSays() {
        assertFalse(LodQuality.HIGH.cutoutLeaves(true));
        assertFalse(LodQuality.HIGH.cutoutLeaves(false));
    }

    @Test
    void highAloneLeavesBladesOut() {
        for (LodQuality quality : LodQuality.values()) {
            assertEquals(quality != LodQuality.HIGH, quality.drawsBlades(), quality.key());
        }
    }

    @Test
    void everyOtherPresetFollowsTheOption() {
        for (LodQuality quality : LodQuality.values()) {
            if (quality != LodQuality.HIGH) {
                assertEquals(true, quality.cutoutLeaves(true), quality.key());
                assertEquals(false, quality.cutoutLeaves(false), quality.key());
            }
        }
    }
}
