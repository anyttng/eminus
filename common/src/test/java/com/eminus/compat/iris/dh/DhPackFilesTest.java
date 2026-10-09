package com.eminus.compat.iris.dh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.eminus.compat.iris.PackContract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DhPackFilesTest {
    private static final String OVERWORLD = "world0";
    private static final String NETHER = "world-1";
    private static final String TERRAIN_VERTEX = "dh_terrain.vsh";
    private static final String TERRAIN_FRAGMENT = "dh_terrain.fsh";

    @TempDir
    Path root;

    @Test
    void dhProgramsInADimensionFolderTakeThePath() throws IOException {
        write(OVERWORLD, TERRAIN_VERTEX, TERRAIN_FRAGMENT);

        assertTrue(DhPackFiles.takesDhPath(root, true, false));
    }

    @Test
    void dhProgramsAtTheRootTakeThePath() throws IOException {
        write("", TERRAIN_VERTEX, TERRAIN_FRAGMENT);

        assertTrue(DhPackFiles.takesDhPath(root, true, false));
    }

    @Test
    void aContractInAnyFolderKeepsThePackOnTheContract() throws IOException {
        write(OVERWORLD, TERRAIN_VERTEX, TERRAIN_FRAGMENT);
        write(NETHER, PackContract.OPAQUE_FILE);

        assertFalse(DhPackFiles.takesDhPath(root, true, false));
    }

    @Test
    void distantHorizonsLoadedLeavesThePathToIris() throws IOException {
        write(OVERWORLD, TERRAIN_VERTEX, TERRAIN_FRAGMENT);

        assertFalse(DhPackFiles.takesDhPath(root, true, true));
    }

    @Test
    void theDefaultSettingKeepsDhProgramsOff() throws IOException {
        write(OVERWORLD, TERRAIN_VERTEX, TERRAIN_FRAGMENT);

        assertFalse(DhPackFiles.takesDhPath(root, false, false));
    }

    @Test
    void halfATerrainProgramTakesNoPath() throws IOException {
        write(OVERWORLD, TERRAIN_FRAGMENT);

        assertFalse(DhPackFiles.takesDhPath(root, true, false));
    }

    @Test
    void aPackWithoutDhProgramsTakesNoPath() throws IOException {
        write(OVERWORLD, "gbuffers_terrain.fsh", "gbuffers_terrain.vsh");

        assertFalse(DhPackFiles.takesDhPath(root, true, false));
    }

    private void write(String folder, String... files) throws IOException {
        Path directory = Files.createDirectories(root.resolve(folder));
        for (String file : files) {
            Files.writeString(directory.resolve(file), "");
        }
    }
}
