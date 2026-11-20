package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class FacePlacementTest {
    private static final int BLOCK = 1;
    private static final int COARSE_BLOCKS = 4;
    private static final int NO_GAP = 0;
    private static final int LOW_GAP = 1;
    private static final int HIGH_GAP = 2;
    private static final float INSET = 0.25F;
    private static final boolean INWARD = true;
    private static final boolean OUTWARD = false;

    @Test
    void lowIsTheModelHeightAtBlockScale() {
        assertEquals(0.5F, FacePlacement.low(0.5F, NO_GAP, BLOCK));
        assertEquals(0.125F, FacePlacement.low(0.5F, NO_GAP, COARSE_BLOCKS));
    }

    @Test
    void lowStandsOnTheLowGap() {
        assertEquals(0.25F, FacePlacement.low(FacePlacement.FLOOR, LOW_GAP, COARSE_BLOCKS));
        assertEquals(0.3125F, FacePlacement.low(0.25F, LOW_GAP, COARSE_BLOCKS));
    }

    @Test
    void highIsTheModelHeightInTheTopBlock() {
        assertEquals(0.5F, FacePlacement.high(0.5F, NO_GAP, BLOCK));
        assertEquals(0.875F, FacePlacement.high(0.5F, NO_GAP, COARSE_BLOCKS));
    }

    @Test
    void highStandsUnderTheHighGap() {
        assertEquals(0.5F, FacePlacement.high(FacePlacement.CEILING, HIGH_GAP, COARSE_BLOCKS));
        assertEquals(0.4375F, FacePlacement.high(0.75F, HIGH_GAP, COARSE_BLOCKS));
    }

    @Test
    void aDownFaceTakesLowOfItsInset() {
        assertEquals(0.3125F, FacePlacement.depth(Direction.DOWN, INSET, LOW_GAP, HIGH_GAP, COARSE_BLOCKS, OUTWARD));
        assertEquals(0.3125F, FacePlacement.depth(Direction.DOWN, INSET, LOW_GAP, HIGH_GAP, COARSE_BLOCKS, INWARD));
    }

    @Test
    void anUpFaceTakesHighOfTheCeilingLessItsInset() {
        assertEquals(0.4375F, FacePlacement.depth(Direction.UP, INSET, LOW_GAP, HIGH_GAP, COARSE_BLOCKS, OUTWARD));
        assertEquals(0.4375F, FacePlacement.depth(Direction.UP, INSET, LOW_GAP, HIGH_GAP, COARSE_BLOCKS, INWARD));
    }

    @Test
    void aSideFaceTakesItsInsetFromItsOwnSideWhateverTheGaps() {
        assertEquals(0.25F, FacePlacement.depth(Direction.NORTH, INSET, LOW_GAP, HIGH_GAP, COARSE_BLOCKS, OUTWARD));
        assertEquals(0.25F, FacePlacement.depth(Direction.WEST, INSET, NO_GAP, NO_GAP, BLOCK, OUTWARD));
        assertEquals(0.75F, FacePlacement.depth(Direction.SOUTH, INSET, LOW_GAP, HIGH_GAP, COARSE_BLOCKS, OUTWARD));
        assertEquals(0.75F, FacePlacement.depth(Direction.EAST, INSET, NO_GAP, NO_GAP, BLOCK, OUTWARD));
    }

    @Test
    void anInwardSideFaceFlipsToTheOppositeSide() {
        assertEquals(0.75F, FacePlacement.depth(Direction.NORTH, INSET, NO_GAP, NO_GAP, BLOCK, INWARD));
        assertEquals(0.25F, FacePlacement.depth(Direction.SOUTH, INSET, NO_GAP, NO_GAP, BLOCK, INWARD));
    }
}
