package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class MeshBufferTest {
    private static final int WATER_BLUE = 0x3F76E4;
    private static final int CORNERS = 0x7F7F_7F80;

    @Test
    void cornersTakeTheirOwnEntryWhileTheTableHasRoom() {
        MeshBuffer buffer = new MeshBuffer();
        int flat = buffer.colourIndex(WATER_BLUE, QuadOffset.NONE);

        assertNotEquals(flat, buffer.cornerIndex(WATER_BLUE, CORNERS));
    }

    @Test
    void cornersOverAFullTableFallBackToTheFlatEntryOfTheirColour() {
        MeshBuffer buffer = new MeshBuffer();
        int flat = buffer.colourIndex(WATER_BLUE, QuadOffset.NONE);
        int last = MeshBuffer.UNTINTED;
        for (int colour = 1; last < MeshBuffer.MAX_COLOURS - 1; colour++) {
            last = buffer.colourIndex(colour, QuadOffset.NONE);
        }

        assertEquals(flat, buffer.cornerIndex(WATER_BLUE, CORNERS));
    }
}
