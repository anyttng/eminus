package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EdgeMaskTest {
    @Test
    void aVoxelOnOneSideFaceReachesNoCellPastItsFaceNeighbour() {
        assertEquals(EdgeMask.NONE, EdgeMask.of(FaceMask.WEST));
        assertEquals(EdgeMask.NONE, EdgeMask.of(FaceMask.UP | FaceMask.SOUTH));
    }

    @Test
    void aVoxelOnAVerticalEdgeReachesTheCellAcrossIt() {
        assertEquals(EdgeMask.bit(1, 0, -1), EdgeMask.of(FaceMask.EAST | FaceMask.NORTH));
    }

    @Test
    void aVoxelOnTheBottomCornerReachesTheCellsBelowItsEdges() {
        assertEquals(EdgeMask.bit(-1, 0, 1) | EdgeMask.bit(-1, -1, 0) | EdgeMask.bit(0, -1, 1)
                | EdgeMask.bit(-1, -1, 1), EdgeMask.of(FaceMask.DOWN | FaceMask.WEST | FaceMask.SOUTH));
    }

    @Test
    void eachSlotReadsBackTheStepsItWasBuiltFrom() {
        for (int stepZ = -1; stepZ <= 1; stepZ++) {
            for (int stepY = -1; stepY <= 1; stepY++) {
                for (int stepX = -1; stepX <= 1; stepX++) {
                    int slot = Integer.numberOfTrailingZeros(EdgeMask.bit(stepX, stepY, stepZ));
                    assertEquals(stepX, EdgeMask.stepX(slot));
                    assertEquals(stepY, EdgeMask.stepY(slot));
                    assertEquals(stepZ, EdgeMask.stepZ(slot));
                }
            }
        }
    }
}
