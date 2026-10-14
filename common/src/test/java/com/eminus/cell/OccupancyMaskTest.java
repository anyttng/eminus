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

    @Test
    void aVoxelLandsInTheGrandchildWhoseCellHoldsIt() {
        int level = 2;
        long parent = CellKey.pack(level, 3, 1, -2);
        CellFrame frame = new CellFrame(0);
        int voxelBlocks = DetailLevel.blocksPerCell(level) / DetailLevel.VOXELS_PER_SIDE;
        int[] corners = {0, 7, 8, 15, 16, 23, 24, 31};

        for (int x : corners) {
            for (int y : corners) {
                for (int z : corners) {
                    int bit = OccupancyMask.grandchildOf(x, y, z);
                    long grandchild = CellKey.child(CellKey.child(parent, bit / OccupancyMask.OCTANTS),
                            bit % OccupancyMask.OCTANTS);
                    int side = DetailLevel.blocksPerCell(level - 2);
                    assertInside(CellFrame.originXOf(parent) + x * voxelBlocks, CellFrame.originXOf(grandchild), side);
                    assertInside(frame.originYOf(parent) + y * voxelBlocks, frame.originYOf(grandchild), side);
                    assertInside(CellFrame.originZOf(parent) + z * voxelBlocks, CellFrame.originZOf(grandchild), side);
                    assertEquals(1 << (bit % OccupancyMask.OCTANTS),
                            OccupancyMask.grandchildren(1L << bit, bit / OccupancyMask.OCTANTS));
                }
            }
        }
    }

    private static void assertInside(int block, int origin, int side) {
        assertTrue(block >= origin && block < origin + side, block + " lies outside " + origin + " + " + side);
    }
}
