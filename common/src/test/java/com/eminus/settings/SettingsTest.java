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
        assertEquals(DetailDistance.MEDIUM, defaults.detailDistance());
        assertTrue(defaults.fog());
        assertTrue(defaults.fade());
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
        Settings base = new Settings(true, 1, 16, 4, DetailDistance.MEDIUM, true, true);

        assertEquals(new Settings(false, 1, 16, 4, DetailDistance.MEDIUM, true, true), base.withIngestion(false));
        assertEquals(new Settings(true, 3, 16, 4, DetailDistance.MEDIUM, true, true), base.withLowestStoredLevel(3));
        assertEquals(new Settings(true, 1, 40, 4, DetailDistance.MEDIUM, true, true), base.withFarRenderCells(40));
        assertEquals(new Settings(true, 1, 16, 9, DetailDistance.MEDIUM, true, true), base.withWorkerThreads(9));
        assertEquals(new Settings(true, 1, 16, 4, DetailDistance.LOW, true, true),
                base.withDetailDistance(DetailDistance.LOW));
        assertEquals(new Settings(true, 1, 16, 4, DetailDistance.MEDIUM, false, true), base.withFog(false));
        assertEquals(new Settings(true, 1, 16, 4, DetailDistance.MEDIUM, true, false), base.withFade(false));
    }
}
