package com.eminus.render.far;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.mesh.QuadGroups;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class GroupFacingTest {
    private static final float MIN = 64.0F;
    private static final float MAX = 96.0F;
    private static final double BELOW = 32.0;
    private static final double INSIDE = 80.0;
    private static final double ABOVE = 128.0;

    private final float[] bounds = {MIN, MIN, MIN, MAX, MAX, MAX};

    @Test
    void anUpwardGroupIsDroppedFromBelowTheCell() {
        assertFalse(GroupFacing.visible(Direction.UP.ordinal(), bounds, INSIDE, BELOW, INSIDE));
    }

    @Test
    void anUpwardGroupIsKeptFromAboveTheCell() {
        assertTrue(GroupFacing.visible(Direction.UP.ordinal(), bounds, INSIDE, ABOVE, INSIDE));
    }

    @Test
    void aDownwardGroupIsDroppedFromAboveTheCell() {
        assertFalse(GroupFacing.visible(Direction.DOWN.ordinal(), bounds, INSIDE, ABOVE, INSIDE));
    }

    @Test
    void bothGroupsOfAnAxisAreKeptWhileTheCameraIsInsideTheCell() {
        assertTrue(GroupFacing.visible(Direction.EAST.ordinal(), bounds, INSIDE, INSIDE, INSIDE));
        assertTrue(GroupFacing.visible(Direction.WEST.ordinal(), bounds, INSIDE, INSIDE, INSIDE));
    }

    @Test
    void everyDirectionalGroupIsDroppedOnItsOwnAxisAlone() {
        assertFalse(GroupFacing.visible(Direction.NORTH.ordinal(), bounds, BELOW, BELOW, ABOVE));
        assertTrue(GroupFacing.visible(Direction.SOUTH.ordinal(), bounds, BELOW, BELOW, ABOVE));
    }

    @Test
    void theDoubleSidedGroupIsNeverDropped() {
        assertTrue(GroupFacing.visible(QuadGroups.DOUBLE_SIDED, bounds, BELOW, BELOW, BELOW));
        assertTrue(GroupFacing.visible(QuadGroups.DOUBLE_SIDED, bounds, ABOVE, ABOVE, ABOVE));
    }
}
