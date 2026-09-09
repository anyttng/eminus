package com.eminus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsFileTest {
    @TempDir
    Path configDir;

    @Test
    void theFirstLoadWritesTheFileWithEveryDefault() throws IOException {
        Path file = configDir.resolve(SettingsService.FILE_NAME);

        assertEquals(Settings.defaults(), SettingsFile.load(file));
        assertTrue(Files.isRegularFile(file));
        assertEquals(Settings.defaults(), SettingsFile.load(file));
    }

    @Test
    void anEditedValueLoadsBack() {
        Path file = configDir.resolve(SettingsService.FILE_NAME);
        Settings edited = new Settings(false, 2, 24, 3, 32, FogMode.OFF);

        SettingsFile.save(file, edited);

        assertEquals(edited, SettingsFile.load(file));
    }

    @Test
    void theSavedFileCarriesTheSettingCommentsAndStillReadsBack() throws IOException {
        Path file = configDir.resolve(SettingsService.FILE_NAME);
        Settings written = new Settings(true, 2, 24, 3, 32, FogMode.FADE);

        SettingsFile.save(file, written);
        String content = Files.readString(file, StandardCharsets.UTF_8);

        assertTrue(content.contains("// Finest detail level kept on disk, 0..4"), content);
        assertTrue(content.contains("// Radius of the far layer, in top-level cells"), content);
        assertTrue(content.contains("// Background worker threads."), content);
        assertTrue(content.contains("// How large a node may look on screen, in pixels"), content);
        assertTrue(content.contains("// What the far layer does where it ends"), content);
        assertEquals(written, SettingsFile.load(file));
    }

    @Test
    void aMalformedValueFallsBackToItsDefault() throws IOException {
        Settings loaded = loadJson("""
                {
                  "ingestion": false,
                  "far_render_cells": "many",
                  "fog_mode": "sideways"
                }
                """);

        assertFalse(loaded.ingestion());
        assertEquals(Settings.DEFAULT_FAR_RENDER_CELLS, loaded.farRenderCells());
        assertEquals(Settings.DEFAULT_FOG_MODE, loaded.fogMode());
    }

    @Test
    void anOutOfRangeValueFallsBackToItsDefault() throws IOException {
        Settings loaded = loadJson("""
                {
                  "lowest_stored_level": 9,
                  "worker_threads": 0
                }
                """);

        assertEquals(Settings.DEFAULT_LOWEST_STORED_LEVEL, loaded.lowestStoredLevel());
        assertEquals(Settings.defaults().workerThreads(), loaded.workerThreads());
    }

    @Test
    void aValueAboveItsMaximumFallsBackToItsDefault() throws IOException {
        Settings defaults = Settings.defaults();
        Settings loaded = loadJson("""
                {
                  "far_render_cells": %d,
                  "worker_threads": %d,
                  "subdivision_size": %d
                }
                """.formatted(Settings.MAX_FAR_RENDER_CELLS + 1, Settings.MAX_WORKER_THREADS + 1,
                Settings.MAX_SUBDIVISION_SIZE + 1));

        assertEquals(defaults.farRenderCells(), loaded.farRenderCells());
        assertEquals(defaults.workerThreads(), loaded.workerThreads());
        assertEquals(defaults.subdivisionSize(), loaded.subdivisionSize());
    }

    @Test
    void aMissingValueFallsBackToItsDefault() throws IOException {
        Settings defaults = Settings.defaults();
        Settings loaded = loadJson("{\"subdivision_size\": 16}");

        assertEquals(16, loaded.subdivisionSize());
        assertEquals(defaults.ingestion(), loaded.ingestion());
        assertEquals(defaults.lowestStoredLevel(), loaded.lowestStoredLevel());
        assertEquals(defaults.farRenderCells(), loaded.farRenderCells());
        assertEquals(defaults.workerThreads(), loaded.workerThreads());
        assertEquals(defaults.fogMode(), loaded.fogMode());
    }

    @Test
    void aKeyTheRecordNoLongerHasIsIgnored() throws IOException {
        Settings loaded = loadJson("""
                {
                  "enabled": true,
                  "ingestion": false,
                  "subdivision_size": 16
                }
                """);

        assertFalse(loaded.ingestion());
        assertEquals(16, loaded.subdivisionSize());
    }

    @Test
    void aFileThatIsNotJsonFallsBackToEveryDefault() throws IOException {
        assertEquals(Settings.defaults(), loadJson("not json at all"));
    }

    private Settings loadJson(String content) throws IOException {
        Path file = configDir.resolve(SettingsService.FILE_NAME);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return SettingsFile.load(file);
    }
}
