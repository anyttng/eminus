package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Collection;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class TreeTraversalBordersTest {
    private static final int CELL = DetailLevel.blocksPerCell(DetailLevel.MAX);
    private static final double INSIDE = CELL / 2.0;
    private static final int FAR_CELLS = 4;
    private static final int ONE_CELL = 1;
    private static final int ONE_REQUEST = 1;
    private static final int TWO_REQUESTS = 2;
    private static final long WALK = 7L;
    private static final int LOWEST_BELOW_TOP = DetailLevel.MAX - 1;
    private static final int LEAF_OCCUPANCY = 0b1;
    private static final int WEST_AND_EAST_OCTANTS = 0b11;
    private static final int SIDE_BY_SIDE_AND_ABOVE_WEST = 0b1_0011;
    private static final int WEST_OCTANT = 0;
    private static final int EAST_OCTANT = 1;
    private static final int NEXT_CELL = 1;
    private static final int SECOND_CELL = 2;
    private static final int WEST = 1 << Direction.WEST.get3DDataValue();
    private static final int EAST = 1 << Direction.EAST.get3DDataValue();

    private final NodeTable nodes = new NodeTable(NodeTable.CAPACITY);
    private final TreeTraversal leaves =
            new TreeTraversal(nodes, new TreeExtent(new CellFrame(0), 1, LOWEST_BELOW_TOP), new TreeHorizon());
    private final long rootKey = CellKey.pack(DetailLevel.MAX, 0, 0, 0);

    @Test
    void aCellIsMarkedTowardsANeighborWithNoNodeYetUntilItLands() {
        TreeNode root = meshedRoot(rootKey, WEST_AND_EAST_OCTANTS);
        leaves.walk(nodes.roots(), inside(), ONE_REQUEST, 0, WALK);
        TreeNode west = root.child(WEST_OCTANT);
        west.meshed(TestMeshes.summary(west.key(), LEAF_OCCUPANCY));

        RenderList waiting = listed(nodes.roots(), ONE_REQUEST, WALK + 1);
        assertEquals(EAST, waiting.borderFaces(west.key()));

        TreeNode east = root.child(EAST_OCTANT);
        east.meshed(TestMeshes.summary(east.key(), LEAF_OCCUPANCY));
        RenderList landed = listed(nodes.roots(), ONE_REQUEST, WALK + 2);
        assertEquals(RenderList.NO_BORDER_FACES, landed.borderFaces(west.key()));
        assertEquals(RenderList.NO_BORDER_FACES, landed.borderFaces(east.key()));
    }

    @Test
    void aCellIsMarkedTowardsANeighborNodeWithoutAMesh() {
        TreeNode root = meshedRoot(rootKey, WEST_AND_EAST_OCTANTS);
        leaves.walk(nodes.roots(), inside(), TWO_REQUESTS, 0, WALK);
        TreeNode west = root.child(WEST_OCTANT);
        west.meshed(TestMeshes.summary(west.key(), LEAF_OCCUPANCY));

        RenderList list = listed(nodes.roots(), TWO_REQUESTS, WALK + 1);

        assertEquals(EAST, list.borderFaces(west.key()));
    }

    @Test
    void aCellIsMarkedTowardsANeighborHiddenUnderAPlaceWaiting() {
        TreeNode root = meshedRoot(rootKey, SIDE_BY_SIDE_AND_ABOVE_WEST);
        leaves.walk(nodes.roots(), inside(), TWO_REQUESTS, 0, WALK);
        TreeNode west = root.child(WEST_OCTANT);
        TreeNode east = root.child(EAST_OCTANT);
        west.meshed(TestMeshes.summary(west.key(), LEAF_OCCUPANCY));
        east.meshed(TestMeshes.summary(east.key(), LEAF_OCCUPANCY));

        RenderList list = listed(nodes.roots(), 0, WALK + 1);

        assertFalse(list.meshes().contains(west.mesh()));
        assertEquals(WEST, list.borderFaces(east.key()));
    }

    @Test
    void aCellIsMarkedTowardsANeighborHeldBehindTheArrivalFront() {
        TreeNode near = meshedRoot(rootKey, OccupancyMask.EMPTY);
        TreeNode shown = meshedRoot(CellKey.pack(DetailLevel.MAX, NEXT_CELL, 0, 0), OccupancyMask.EMPTY);
        listed(nodes.roots(), 0, WALK);

        TreeNode arriving = meshedRoot(CellKey.pack(DetailLevel.MAX, SECOND_CELL, 0, 0), OccupancyMask.EMPTY);
        near.meshed(TestMeshes.summary(rootKey, LEAF_OCCUPANCY));
        RenderList list = listed(nodes.roots(), 0, WALK + 1);

        assertFalse(list.meshes().contains(arriving.mesh()));
        assertEquals(WEST | EAST, list.borderFaces(shown.key()));
    }

    @Test
    void theFarEdgeGainsNoWallTowardsWhatLiesPastIt() {
        TreeNode edge = meshedRoot(CellKey.pack(DetailLevel.MAX, NEXT_CELL, 0, 0), OccupancyMask.EMPTY);
        nodes.root(CellKey.pack(DetailLevel.MAX, SECOND_CELL, 0, 0));
        CameraFrame camera = FakeCameras.everything(INSIDE, INSIDE, INSIDE, ONE_CELL,
                FakeCameras.FAR_PIXELS_PER_BLOCK);

        leaves.walk(nodes.roots(), camera, 0, 0, WALK);
        RenderList list = leaves.list(camera);

        assertEquals(RenderList.NO_BORDER_FACES, list.borderFaces(edge.key()));
    }

    private RenderList listed(Collection<TreeNode> roots, int budget, long walk) {
        leaves.walk(roots, inside(), budget, 0, walk);
        return leaves.list(inside());
    }

    private TreeNode meshedRoot(long key, int occupancy) {
        TreeNode root = nodes.root(key);
        root.meshed(TestMeshes.summary(key, occupancy));
        return root;
    }

    private static CameraFrame inside() {
        return FakeCameras.everything(INSIDE, INSIDE, INSIDE, FAR_CELLS, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }
}
