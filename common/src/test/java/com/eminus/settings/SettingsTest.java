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
        assertTrue(defaults.fog());
        assertEquals(Settings.defaultWorkerThreads(Runtime.getRuntime().availableProcessors()),
                defaults.workerThreads());
    }

    @Test
    void workerThreadsAreTheCoreCountDividedByOneAndAHalf() {
        assertEquals(2, Settings.defaultWorkerThreads(3));
        assertEquals(5, Settings.defaultWorkerThreads(8));
        assertEquals(8, Settings.defaultWorkerThreads(12));
    }

    @Test
    void workerThreadsNeverDropBelowOne() {
        assertEquals(1, Settings.defaultWorkerThreads(1));
        assertEquals(1, Settings.defaultWorkerThreads(2));
    }

    @Test
    void workerThreadsNeverRiseAboveEight() {
        assertEquals(8, Settings.defaultWorkerThreads(16));
        assertEquals(8, Settings.defaultWorkerThreads(1024));
    }

    @Test
    void eachWitherChangesItsOwnFieldAlone() {
        Settings base = new Settings(true, 1, 16, 4, 64, true);

        assertEquals(new Settings(false, 1, 16, 4, 64, true), base.withIngestion(false));
        assertEquals(new Settings(true, 3, 16, 4, 64, true), base.withLowestStoredLevel(3));
        assertEquals(new Settings(true, 1, 40, 4, 64, true), base.withFarRenderCells(40));
        assertEquals(new Settings(true, 1, 16, 9, 64, true), base.withWorkerThreads(9));
        assertEquals(new Settings(true, 1, 16, 4, 128, true), base.withSubdivisionSize(128));
        assertEquals(new Settings(true, 1, 16, 4, 64, false), base.withFog(false));
    }
}
