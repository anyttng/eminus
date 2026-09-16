package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OccupancyMaskTest {
    @Test
    void theOctantIsOneBitPerAxisHalf() {
        assertEquals(0, OccupancyMask.octantOf(0, 0, 0));
        assertEquals(0, OccupancyMask.octantOf(15, 15, 15));
        assertEquals(1, OccupancyMask.octantOf(31, 0, 0));
        assertEquals(2, OccupancyMask.octantOf(0, 0, 31));
        assertEquals(4, OccupancyMask.octantOf(0, 31, 0));
        assertEquals(7, OccupancyMask.octantOf(16, 16, 16));
    }

    @Test
    void setAndClearMoveOneBitEach() {
        int mask = OccupancyMask.EMPTY;
        assertTrue(OccupancyMask.isEmpty(mask));

        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            mask = OccupancyMask.set(mask, octant);
        }

        assertFalse(OccupancyMask.isEmpty(mask));

        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            assertTrue(OccupancyMask.isSet(mask, octant));
            mask = OccupancyMask.clear(mask, octant);
            assertFalse(OccupancyMask.isSet(mask, octant));
        }

        assertTrue(OccupancyMask.isEmpty(mask));
    }
}
