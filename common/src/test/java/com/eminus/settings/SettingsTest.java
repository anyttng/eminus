package com.eminus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsTest {
    @Test
    void defaultsCarryEverySettingOfTheFirstVersion() {
        Settings defaults = Settings.defaults();

        assertTrue(defaults.ingestion());
        assertEquals(0, defaults.lowestStoredLevel());
        assertEquals(16, defaults.farRenderCells());
        assertEquals(64, defaults.subdivisionSize());
        assertEquals(FogMode.FOG_AND_FADE, defaults.fogMode());
        assertEquals(Settings.defaultWorkerThreads(Runtime.getRuntime().availableProcessors()),
                defaults.workerThreads());
    }

    @Test
    void workerThreadsAreTheCoreCountDividedByOneAndAHalf() {
        assertEquals(2, Settings.defaultWorkerThreads(3));
        assertEquals(5, Settings.defaultWorkerThreads(8));
        assertEquals(8, Settings.defaultWorkerThreads(12));
        assertEquals(10, Settings.defaultWorkerThreads(16));
    }

    @Test
    void workerThreadsNeverDropBelowOne() {
        assertEquals(1, Settings.defaultWorkerThreads(1));
        assertEquals(1, Settings.defaultWorkerThreads(2));
    }
}
