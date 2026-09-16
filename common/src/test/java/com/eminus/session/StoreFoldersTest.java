package com.eminus.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class StoreFoldersTest {
    private static final WorldIdentity IDENTITY = new WorldIdentity("New World", 8675309L, "minecraft:overworld");

    @Test
    void theSingleplayerBaseSitsUnderTheSave() {
        assertEquals(Path.of("saves", "New World", StoreFolders.ROOT_FOLDER),
                StoreFolders.singleplayerBase(Path.of("saves", "New World")));
    }

    @Test
    void theMultiplayerBaseSitsUnderTheGameFolder() {
        assertEquals(Path.of("game", StoreFolders.ROOT_FOLDER, StoreFolders.SERVERS_FOLDER, "mc.example.com_25565"),
                StoreFolders.multiplayerBase(Path.of("game"), "mc.example.com:25565"));
    }

    @Test
    void theDimensionFolderIsTheIdentityHashUnderTheBase() {
        Path base = StoreFolders.singleplayerBase(Path.of("saves", "New World"));

        assertEquals(base.resolve(IDENTITY.folderName()), StoreFolders.dimensionFolder(base, IDENTITY));
    }

    @Test
    void theSaveRootSegmentVanillaAppendsIsDropped() {
        assertEquals(StoreFolders.singleplayerBase(Path.of("saves", "New World")),
                StoreFolders.singleplayerBase(Path.of("saves", "New World", ".")));
    }

    @Test
    void aRelativeGameFolderIsFlattenedToo() {
        assertEquals(Path.of(StoreFolders.ROOT_FOLDER, StoreFolders.SERVERS_FOLDER, "localhost"),
                StoreFolders.multiplayerBase(Path.of("."), "localhost"));
    }

    @Test
    void anAddressWithoutUsableCharactersFallsBackToOneName() {
        assertEquals(StoreFolders.UNNAMED_SERVER_FOLDER, StoreFolders.serverFolderName(""));
    }
}
