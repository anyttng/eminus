package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class TranslucentOrderTest {
    private static final int GLASS = 1;
    private static final int INWARD_GLASS = 2;
    private static final int FINEST_LEVEL = 0;
    private static final int COARSE_LEVEL = 2;
    private static final int LOW_GAP = 1;
    private static final int HIGH_GAP = 2;
    private static final int GAPS = HIGH_GAP << VoxelEntry.HIGH_GAP_SHIFT | LOW_GAP << VoxelEntry.LOW_GAP_SHIFT;
    private static final float INSET = 0.25F;
    private static final int VOXEL = 5;
    private static final int FACE_SHIFT = 24;
    private static final int STEPS_PER_VOXEL = 1 << 16;
    private static final int MARGIN_VOXELS = 1;
    private static final int FARTHEST_PLANE = (DetailLevel.VOXELS_PER_SIDE + 2 * MARGIN_VOXELS) * STEPS_PER_VOXEL;

    private final FakeModels models = new FakeModels();

    TranslucentOrderTest() {
        models.describe(GLASS, ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.ALL, 0,
                ModelMetadata.TRANSLUCENT));
        models.describe(INWARD_GLASS, ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.ALL, 0,
                ModelMetadata.TRANSLUCENT | ModelMetadata.INWARD));
        for (Direction face : Direction.values()) {
            models.inset(GLASS, face, INSET);
            models.inset(INWARD_GLASS, face, INSET);
        }
    }

    @Test
    void aDownFaceStandsItsInsetAboveTheVoxelFloor() {
        assertEquals(expected(Direction.DOWN, 0.25F), order(Direction.DOWN, GLASS, FINEST_LEVEL, QuadPlacement.NONE));
    }

    @Test
    void anUpFaceStandsItsInsetBelowTheVoxelCeiling() {
        assertEquals(expected(Direction.UP, 0.75F), order(Direction.UP, GLASS, FINEST_LEVEL, QuadPlacement.NONE));
    }

    @Test
    void aSideFaceStandsItsInsetInsideTheVoxelFromItsOwnSide() {
        assertEquals(expected(Direction.NORTH, 0.25F),
                order(Direction.NORTH, GLASS, FINEST_LEVEL, QuadPlacement.NONE));
        assertEquals(expected(Direction.SOUTH, 0.75F),
                order(Direction.SOUTH, GLASS, FINEST_LEVEL, QuadPlacement.NONE));
    }

    @Test
    void anInwardSideFaceStandsOnTheOppositeSide() {
        assertEquals(expected(Direction.NORTH, 0.75F),
                order(Direction.NORTH, INWARD_GLASS, FINEST_LEVEL, QuadPlacement.NONE));
        assertEquals(expected(Direction.SOUTH, 0.25F),
                order(Direction.SOUTH, INWARD_GLASS, FINEST_LEVEL, QuadPlacement.NONE));
    }

    @Test
    void aGappedDownFaceStandsOnItsLowGapAtBlockScale() {
        assertEquals(expected(Direction.DOWN, 0.3125F), order(Direction.DOWN, GLASS, COARSE_LEVEL, GAPS));
    }

    @Test
    void aGappedUpFaceStandsUnderItsHighGapAtBlockScale() {
        assertEquals(expected(Direction.UP, 0.4375F), order(Direction.UP, GLASS, COARSE_LEVEL, GAPS));
    }

    @Test
    void gapsLeaveASideFaceWhereItsInsetPutsIt() {
        assertEquals(expected(Direction.NORTH, 0.25F), order(Direction.NORTH, GLASS, COARSE_LEVEL, GAPS));
        assertEquals(expected(Direction.NORTH, 0.75F), order(Direction.NORTH, INWARD_GLASS, COARSE_LEVEL, GAPS));
    }

    @Test
    void anInwardFlagLeavesAVerticalFaceWhereItStands() {
        assertEquals(expected(Direction.DOWN, 0.3125F), order(Direction.DOWN, INWARD_GLASS, COARSE_LEVEL, GAPS));
        assertEquals(expected(Direction.UP, 0.4375F), order(Direction.UP, INWARD_GLASS, COARSE_LEVEL, GAPS));
    }

    private int order(Direction face, int modelId, int level, int placement) {
        long quad = Quad.of(Quad.data(0, modelId, 0), face.get3DDataValue(), VOXEL, VOXEL, VOXEL, 1, 1);
        return TranslucentOrder.of(quad, level, placement, models);
    }

    private static int expected(Direction face, float depth) {
        int plane = (VOXEL + MARGIN_VOXELS) * STEPS_PER_VOXEL + Math.round(depth * STEPS_PER_VOXEL);
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        return face.get3DDataValue() << FACE_SHIFT | (positive ? plane : FARTHEST_PLANE - plane);
    }
}
