package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MeshBufferTest {
    private static final int WATER_BLUE = 0x3F76E4;
    private static final int SWAMP_GREEN = 0x6A7039;
    private static final int CORNERS = 0x7F7F_7F80;

    @Test
    void cornersTakeTheirOwnEntryWhileTheTableHasRoom() {
        MeshBuffer buffer = new MeshBuffer();
        int flat = buffer.colourIndex(WATER_BLUE, QuadOffset.NONE);

        assertNotEquals(flat, buffer.colourIndex(WATER_BLUE, CORNERS));
    }

    @Test
    void cornersOverAFullTableFallBackToTheFlatEntryOfTheirColour() {
        MeshBuffer buffer = new MeshBuffer();
        int flat = buffer.colourIndex(WATER_BLUE, QuadOffset.NONE);
        fill(buffer);

        assertEquals(flat, buffer.colourIndex(WATER_BLUE, CORNERS));
        assertTrue(buffer.lostPlacements());
    }

    @Test
    void aFullTableSubstitutesTheColourOfAPlacementItHolds() {
        MeshBuffer buffer = new MeshBuffer();
        buffer.colourIndex(WATER_BLUE, CORNERS);
        fill(buffer);

        assertEquals(CORNERS, buffer.offsetAt(buffer.colourIndex(SWAMP_GREEN, CORNERS)));
        assertFalse(buffer.lostPlacements());
    }

    @Test
    void aReservingResetGivesALostPlacementItsEntryBeforeTheTableFills() {
        MeshBuffer buffer = new MeshBuffer();
        fill(buffer);
        buffer.colourIndex(WATER_BLUE, CORNERS);

        buffer.resetReserving();
        fill(buffer);

        assertEquals(CORNERS, buffer.offsetAt(buffer.colourIndex(WATER_BLUE, CORNERS)));
        assertFalse(buffer.lostPlacements());
    }

    private static void fill(MeshBuffer buffer) {
        for (int colour = 1; colour < MeshBuffer.MAX_COLOURS; colour++) {
            buffer.colourIndex(colour, QuadOffset.NONE);
        }
    }
}
