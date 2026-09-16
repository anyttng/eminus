package com.eminus.client.render.far;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.settings.DetailDistance;
import com.eminus.settings.Settings;

import org.junit.jupiter.api.Test;

class FarRendererTest {
    private static final Settings BUILT = new Settings(true, 0, 16, 4, DetailDistance.MEDIUM, true, true);

    @Test
    void aFarDistanceChangeRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, new Settings(true, 0, 32, 4, DetailDistance.MEDIUM, true, true)));
    }

    @Test
    void aDetailDistanceChangeRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, new Settings(true, 0, 16, 4, DetailDistance.HIGH, true, true)));
    }

    @Test
    void aFogOrFadeChangeKeepsTheRenderer() {
        assertFalse(FarRenderer.recreates(BUILT, new Settings(true, 0, 16, 4, DetailDistance.MEDIUM, false, false)));
    }

    @Test
    void anIngestionOrWorkerChangeKeepsTheRenderer() {
        assertFalse(FarRenderer.recreates(BUILT, new Settings(false, 0, 16, 8, DetailDistance.MEDIUM, true, true)));
    }
}
