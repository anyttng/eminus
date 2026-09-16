package com.eminus.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldIdentityTest {
    private static final String WORLD = "New World";
    private static final long SEED = 8675309L;
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String HEX = "[0-9a-f]+";

    @Test
    void theSameWorldAndDimensionAlwaysGiveTheSameFolder() {
        assertEquals(new WorldIdentity(WORLD, SEED, OVERWORLD).folderName(),
                new WorldIdentity(WORLD, SEED, OVERWORLD).folderName());
    }

    @Test
    void theFolderNameIsHexOfTheDeclaredLength() {
        String name = new WorldIdentity(WORLD, SEED, OVERWORLD).folderName();

        assertEquals(WorldIdentity.FOLDER_NAME_LENGTH, name.length());
        assertTrue(name.matches(HEX));
    }

    @Test
    void eachDimensionOfOneWorldGetsItsOwnFolder() {
        assertNotEquals(new WorldIdentity(WORLD, SEED, OVERWORLD).folderName(),
                new WorldIdentity(WORLD, SEED, NETHER).folderName());
    }

    @Test
    void twoSeedsGiveTwoFolders() {
        assertNotEquals(new WorldIdentity(WORLD, SEED, OVERWORLD).folderName(),
                new WorldIdentity(WORLD, SEED + 1, OVERWORLD).folderName());
    }

    @Test
    void theWorldNameStaysOutOfTheFolderName() {
        assertEquals(new WorldIdentity(WORLD, SEED, OVERWORLD).folderName(),
                new WorldIdentity("Another World", SEED, OVERWORLD).folderName());
    }
}
