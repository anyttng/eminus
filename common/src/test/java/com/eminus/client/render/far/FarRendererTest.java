package com.eminus.client.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.eminus.gpu.Location;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.settings.CornerOcclusion;
import com.eminus.settings.DetailDistance;
import com.eminus.settings.LeafMode;
import com.eminus.settings.LodChoice;
import com.eminus.settings.Settings;
import com.eminus.settings.ShaderPackLod;

import org.junit.jupiter.api.Test;

class FarRendererTest {
    private static final Settings BUILT = new Settings(true, LodChoice.ULTRA, Settings.DEFAULT_CUSTOM_LOD, 16, 4, DetailDistance.MEDIUM, true, true, ShaderPackLod.DEFAULT, true);
    private static final Location MASK = new Location("eminus", "near_mask");
    private static final Location OPAQUE = new Location("eminus", "far_opaque");
    private static final Location TRANSLUCENT = new Location("eminus", "far_translucent");

    private record FakePipeline(Location location, boolean compiles) implements Pipeline {
    }

    @Test
    void theFirstProgramThatDoesNotCompileRefusesTheRenderer() {
        assertEquals(OPAQUE, FarRenderer.refusedProgram(List.of(new FakePipeline(MASK, true),
                new FakePipeline(OPAQUE, false), new FakePipeline(TRANSLUCENT, false))));
    }

    @Test
    void programsThatAllCompileRefuseNothing() {
        assertNull(FarRenderer.refusedProgram(List.of(new FakePipeline(MASK, true), new FakePipeline(OPAQUE, true),
                new FakePipeline(TRANSLUCENT, true))));
    }

    @Test
    void aFarDistanceChangeRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, new Settings(true, LodChoice.ULTRA, Settings.DEFAULT_CUSTOM_LOD, 32, 4, DetailDistance.MEDIUM, true, true, ShaderPackLod.DEFAULT, true)));
    }

    @Test
    void aDetailDistanceChangeRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, new Settings(true, LodChoice.ULTRA, Settings.DEFAULT_CUSTOM_LOD, 16, 4, DetailDistance.HIGH, true, true, ShaderPackLod.DEFAULT, true)));
    }

    @Test
    void aLodQualityChangeRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, BUILT.withLodChoice(LodChoice.HIGH)));
    }

    @Test
    void aChangeOfOneLodDetailRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, BUILT.withLodChoice(LodChoice.CUSTOM).withCustomLod(BUILT.lodQuality().withDrawsBlades(false))));
        assertTrue(FarRenderer.recreates(BUILT, BUILT.withLodChoice(LodChoice.CUSTOM).withCustomLod(BUILT.lodQuality().withLeaves(LeafMode.OPAQUE))));
        assertTrue(FarRenderer.recreates(BUILT, BUILT.withLodChoice(LodChoice.CUSTOM).withCustomLod(BUILT.lodQuality().withCornerOcclusion(CornerOcclusion.OFF))));
    }

    @Test
    void aTextureResolutionChangeKeepsTheRenderer() {
        assertFalse(FarRenderer.recreates(BUILT, BUILT.withLodChoice(LodChoice.CUSTOM).withCustomLod(BUILT.lodQuality().withFullTextures(false))));
    }

    @Test
    void aSwitchBetweenHighAndMediumKeepsTheRenderer() {
        Settings high = BUILT.withLodChoice(LodChoice.HIGH);

        assertFalse(FarRenderer.recreates(high, high.withLodChoice(LodChoice.MEDIUM)));
        assertFalse(FarRenderer.recreates(high.withLodChoice(LodChoice.MEDIUM), high));
    }

    @Test
    void aTextureResolutionChangeBesideAnotherDetailRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, BUILT.withLodChoice(LodChoice.CUSTOM).withCustomLod(BUILT.lodQuality().withFullTextures(false).withLeaves(LeafMode.OPAQUE))));
    }

    @Test
    void customWithThePresetsOwnValuesKeepsTheRenderer() {
        Settings high = BUILT.withLodChoice(LodChoice.HIGH);
        Settings custom = high.withCustomLod(LodChoice.HIGH.presetQuality().orElseThrow()).withLodChoice(LodChoice.CUSTOM);

        assertFalse(FarRenderer.recreates(high, custom));
    }

    @Test
    void aCustomSetEditedUnderAPresetKeepsTheRenderer() {
        assertFalse(FarRenderer.recreates(BUILT, BUILT.withCustomLod(BUILT.customLod().withDrawsBlades(false))));
    }

    @Test
    void aFogOrFadeChangeKeepsTheRenderer() {
        assertFalse(FarRenderer.recreates(BUILT, new Settings(true, LodChoice.ULTRA, Settings.DEFAULT_CUSTOM_LOD, 16, 4, DetailDistance.MEDIUM, false, false, ShaderPackLod.DEFAULT, true)));
    }

    @Test
    void anIngestionOrWorkerChangeKeepsTheRenderer() {
        assertFalse(FarRenderer.recreates(BUILT, new Settings(false, LodChoice.ULTRA, Settings.DEFAULT_CUSTOM_LOD, 16, 8, DetailDistance.MEDIUM, true, true, ShaderPackLod.DEFAULT, true)));
    }
}
