package com.eminus.render.far.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.settings.FogMode;
import com.eminus.settings.Settings;

import org.junit.jupiter.api.Test;

class FarRendererTest {
    private static final Settings BUILT = new Settings(true, 0, 16, 4, 64, FogMode.FOG_AND_FADE);

    @Test
    void aFarDistanceChangeRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, new Settings(true, 0, 32, 4, 64, FogMode.FOG_AND_FADE)));
    }

    @Test
    void aSubdivisionSizeChangeRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, new Settings(true, 0, 16, 4, 32, FogMode.FOG_AND_FADE)));
    }

    @Test
    void aFogModeChangeKeepsTheRenderer() {
        assertFalse(FarRenderer.recreates(BUILT, new Settings(true, 0, 16, 4, 64, FogMode.OFF)));
    }

    @Test
    void anIngestionOrWorkerChangeKeepsTheRenderer() {
        assertFalse(FarRenderer.recreates(BUILT, new Settings(false, 0, 16, 8, 64, FogMode.FOG_AND_FADE)));
    }
}
