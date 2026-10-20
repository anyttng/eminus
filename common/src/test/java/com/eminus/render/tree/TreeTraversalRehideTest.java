package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;

import org.junit.jupiter.api.Test;

class TreeTraversalRehideTest {
    private static final int CELL = DetailLevel.blocksPerCell(DetailLevel.MAX);
    private static final double INSIDE = CELL / 2.0;
    private static final double TWO_CELLS_AWAY = 2.0 * CELL + INSIDE;
    private static final double INSIDE_UPPER = CELL + INSIDE;
    private static final int FAR_CELLS = 4;
    private static final int BUDGET = 8;
    private static final int NO_OUT_OF_VIEW = 0;
    private static final long WALK = 7L;
    private static final int TWO_OCTANTS = 0b11;
    private static final int TWO_CELLS_HIGH = 2;
    private static final int UPPER_CELL = 1;

    private final NodeTable nodes = new NodeTable(NodeTable.CAPACITY);
    private final TreeTraversal traversal = new TreeTraversal(nodes,
            new TreeExtent(new CellFrame(0), TWO_CELLS_HIGH, DetailLevel.MIN), new TreeHorizon());
    private final long lowerKey = CellKey.pack(DetailLevel.MAX, 0, 0, 0);
    private final long upperKey = CellKey.pack(DetailLevel.MAX, 0, UPPER_CELL, 0);

    @Test
    void aShownCellStaysListedUnderAPlaceNowWaitingAboveIt() {
        TreeNode lower = meshedRoot(lowerKey, OccupancyMask.EMPTY);
        meshedRoot(upperKey, TWO_OCTANTS);

        assertTrue(list(far(), WALK).meshes().contains(lower.mesh()));
        assertTrue(list(inside(), WALK + 1).meshes().contains(lower.mesh()));
    }

    @Test
    void aCellNeverShownWaitsForAPlaceWaitingInViewBelowIt() {
        meshedRoot(lowerKey, TWO_OCTANTS);
        TreeNode upper = meshedRoot(upperKey, OccupancyMask.EMPTY);

        assertFalse(list(inside(), WALK).meshes().contains(upper.mesh()));
    }

    @Test
    void aCellNeverShownDrawsOverAPlaceWaitingOutOfViewBelowIt() {
        meshedRoot(lowerKey, TWO_OCTANTS);
        TreeNode upper = meshedRoot(upperKey, OccupancyMask.EMPTY);
        CameraFrame level = FakeCameras.looking(INSIDE, INSIDE_UPPER, INSIDE, 1.0F, 0.0F, 0.0F, FAR_CELLS,
                FakeCameras.CLOSE_PIXELS_PER_BLOCK);

        assertTrue(list(level, WALK).meshes().contains(upper.mesh()));
    }

    private RenderList list(CameraFrame camera, long walk) {
        traversal.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, walk);
        return traversal.list(camera);
    }

    private TreeNode meshedRoot(long key, int occupancy) {
        TreeNode root = nodes.root(key);
        root.meshed(TestMeshes.summary(key, occupancy));
        return root;
    }

    private static CameraFrame inside() {
        return FakeCameras.everything(INSIDE, INSIDE, INSIDE, FAR_CELLS, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private static CameraFrame far() {
        return FakeCameras.everything(TWO_CELLS_AWAY, INSIDE, INSIDE, FAR_CELLS, FakeCameras.FAR_PIXELS_PER_BLOCK);
    }
}
