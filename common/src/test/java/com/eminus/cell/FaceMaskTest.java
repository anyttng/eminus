package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class FaceMaskTest {
    private static final int LAST = DetailLevel.VOXELS_PER_SIDE - 1;
    private static final int INSIDE = 5;

    @Test
    void eachBitSitsAtItsDirectionOrdinal() {
        assertEquals(Direction.DOWN.ordinal(), Integer.numberOfTrailingZeros(FaceMask.DOWN));
        assertEquals(Direction.UP.ordinal(), Integer.numberOfTrailingZeros(FaceMask.UP));
        assertEquals(Direction.NORTH.ordinal(), Integer.numberOfTrailingZeros(FaceMask.NORTH));
        assertEquals(Direction.SOUTH.ordinal(), Integer.numberOfTrailingZeros(FaceMask.SOUTH));
        assertEquals(Direction.WEST.ordinal(), Integer.numberOfTrailingZeros(FaceMask.WEST));
        assertEquals(Direction.EAST.ordinal(), Integer.numberOfTrailingZeros(FaceMask.EAST));
    }

    @Test
    void theLowCornerCarriesTheThreeNegativeFaces() {
        assertEquals(FaceMask.DOWN | FaceMask.NORTH | FaceMask.WEST, FaceMask.of(0, 0, 0));
    }

    @Test
    void theHighCornerCarriesTheThreePositiveFaces() {
        assertEquals(FaceMask.UP | FaceMask.SOUTH | FaceMask.EAST, FaceMask.of(LAST, LAST, LAST));
    }

    @Test
    void anInteriorVoxelCarriesNoFace() {
        assertEquals(FaceMask.NONE, FaceMask.of(INSIDE, INSIDE, INSIDE));
    }
}
