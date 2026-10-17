package com.eminus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class LodQualityTest {
    private static final LodQuality CUSTOM = new LodQuality(2, LeafMode.SEE_THROUGH, true, false, CornerOcclusion.EVERY_LEVEL);

    @Test
    void ultraDrawsSeeThroughLeaves() {
        assertTrue(preset(LodChoice.ULTRA).cutoutLeaves());
    }

    @Test
    void everyOtherPresetDrawsLeavesOpaque() {
        for (LodChoice choice : presets()) {
            if (choice != LodChoice.ULTRA) {
                assertFalse(preset(choice).cutoutLeaves(), choice.key());
            }
        }
    }

    @Test
    void eachLeafModeCutsOutOrNot() {
        assertTrue(LeafMode.SEE_THROUGH.cutout());
        assertFalse(LeafMode.OPAQUE.cutout());
        assertEquals(LeafMode.SEE_THROUGH, LeafMode.of(true));
        assertEquals(LeafMode.OPAQUE, LeafMode.of(false));
    }

    @Test
    void anOldStoredLevelMapsToTheFirstPresetStoringIt() {
        assertEquals(LodChoice.ULTRA, LodChoice.fromLowestStoredLevel(0));
        assertEquals(LodChoice.MEDIUM, LodChoice.fromLowestStoredLevel(1));
        assertEquals(LodChoice.LOW, LodChoice.fromLowestStoredLevel(2));
        assertEquals(LodChoice.MINIMAL, LodChoice.fromLowestStoredLevel(3));
        assertEquals(LodChoice.MINIMAL, LodChoice.fromLowestStoredLevel(4));
    }

    @Test
    void ultraAloneDrawsBlades() {
        for (LodChoice choice : presets()) {
            assertEquals(choice == LodChoice.ULTRA, preset(choice).drawsBlades(), choice.key());
        }
    }

    @Test
    void lowAndMinimalAloneUploadTheAtlasFromTheFirstMip() {
        assertEquals(0, preset(LodChoice.ULTRA).atlasFirstMip());
        assertEquals(0, preset(LodChoice.HIGH).atlasFirstMip());
        assertEquals(0, preset(LodChoice.MEDIUM).atlasFirstMip());
        assertEquals(1, preset(LodChoice.LOW).atlasFirstMip());
        assertEquals(1, preset(LodChoice.MINIMAL).atlasFirstMip());
    }

    @Test
    void aPresetIgnoresTheCustomSetAndCustomTakesIt() {
        assertEquals(preset(LodChoice.MEDIUM), LodChoice.MEDIUM.quality(CUSTOM));
        assertEquals(CUSTOM, LodChoice.CUSTOM.quality(CUSTOM));
        assertEquals(Optional.empty(), LodChoice.CUSTOM.presetQuality());
    }

    @Test
    void customStaysCustomWhenItsValuesEqualAPreset() {
        assertEquals(preset(LodChoice.HIGH), LodChoice.CUSTOM.quality(preset(LodChoice.HIGH)));
        assertEquals(LodChoice.CUSTOM, Settings.defaults().withLodChoice(LodChoice.CUSTOM)
                .withCustomLod(preset(LodChoice.HIGH)).lodChoice());
    }

    @Test
    void everyChoiceReadsBackFromItsKey() {
        for (LodChoice choice : LodChoice.values()) {
            assertEquals(Optional.of(choice), LodChoice.fromKey(choice.key()));
        }
    }

    @Test
    void everyPresetDarkensCornersOnTheFinestLevelOnly() {
        for (LodChoice choice : presets()) {
            assertEquals(CornerOcclusion.FINEST_LEVEL, preset(choice).cornerOcclusion(), choice.key());
        }
    }

    @Test
    void everyCornerOcclusionReadsBackFromItsKey() {
        for (CornerOcclusion occlusion : CornerOcclusion.values()) {
            assertEquals(Optional.of(occlusion), CornerOcclusion.fromKey(occlusion.key()));
        }
    }

    @Test
    void eachDetailActsOnItsOwn() {
        LodQuality seeThroughAlone = preset(LodChoice.HIGH).withLeaves(LeafMode.SEE_THROUGH);

        assertTrue(seeThroughAlone.cutoutLeaves());
        assertFalse(seeThroughAlone.drawsBlades());
        assertEquals(0, seeThroughAlone.atlasFirstMip());
        assertEquals(1, preset(LodChoice.HIGH).withFullTextures(false).atlasFirstMip());
    }

    @Test
    void aStoredLevelOutsideTheSliderIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new LodQuality(LodQuality.MAX_STORED_LEVEL + 1, LeafMode.OPAQUE, false, true, CornerOcclusion.EVERY_LEVEL));
        assertThrows(IllegalArgumentException.class,
                () -> new LodQuality(LodQuality.MIN_STORED_LEVEL - 1, LeafMode.OPAQUE, false, true, CornerOcclusion.EVERY_LEVEL));
    }

    private static LodQuality preset(LodChoice choice) {
        return choice.presetQuality().orElseThrow();
    }

    private static LodChoice[] presets() {
        return new LodChoice[] {LodChoice.ULTRA, LodChoice.HIGH, LodChoice.MEDIUM, LodChoice.LOW, LodChoice.MINIMAL};
    }
}
