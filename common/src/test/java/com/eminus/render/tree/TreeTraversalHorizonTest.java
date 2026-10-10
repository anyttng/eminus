package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;
import com.eminus.mesh.MeshSummary;

import org.junit.jupiter.api.Test;

class TreeTraversalHorizonTest {
    private static final int CELL = DetailLevel.blocksPerCell(DetailLevel.MAX);
    private static final double INSIDE = CELL / 2.0;
    private static final double PAST_THE_EDGE = 32.0;
    private static final int FAR_CELLS = 4;
    private static final int NEXT_CELL = 1;
    private static final int ALL_OCTANTS = 0xFF;
    private static final int BUDGET = 8;
    private static final int SMALL_BUDGET = 3;
    private static final long WALK = 7L;
    private static final double BETWEEN_THE_ROOTS = 5.0;
    private static final double NOTHING_REFINES = 0.0;

    private final NodeTable nodes = new NodeTable(NodeTable.CAPACITY);
    private final TreeExtent extent = new TreeExtent(new CellFrame(0), 1, DetailLevel.MIN);
    private final TreeHorizon horizon = new TreeHorizon();
    private final TreeTraversal traversal = new TreeTraversal(nodes, extent, horizon);
    private final TreeNode behind = meshedRoot(CellKey.pack(DetailLevel.MAX, 0, 0, 0));
    private final TreeNode ahead = meshedRoot(CellKey.pack(DetailLevel.MAX, NEXT_CELL, 0, 0));

    @Test
    void aParentPastTheHorizonIsNeverRequestedInViewOrOut() {
        horizon.lower(BETWEEN_THE_ROOTS);

        traversal.walk(nodes.roots(), turnedAway(), BUDGET + SMALL_BUDGET, BUDGET + SMALL_BUDGET, WALK);

        assertEquals(OccupancyMask.OCTANTS, traversal.requested().size());
        for (TreeNode child : traversal.requested()) {
            assertSame(ahead, child.parent());
        }

        assertTrue(traversal.outOfViewRequested().isEmpty());
    }

    @Test
    void pastTheHorizonTheParentIsDrawnOverItsChildrenInViewAndOut() {
        horizon.lower(NOTHING_REFINES);
        List<MeshSummary> children = new ArrayList<>(meshedChildren(behind));
        children.addAll(meshedChildren(ahead));

        traversal.walk(nodes.roots(), turnedAway(), BUDGET, BUDGET, WALK);
        RenderList list = traversal.list(turnedAway());

        assertTrue(list.meshes().contains(behind.mesh()));
        assertTrue(list.meshes().contains(ahead.mesh()));
        for (MeshSummary child : children) {
            assertFalse(list.meshes().contains(child));
        }

        assertTrue(traversal.requested().isEmpty());
        assertTrue(traversal.outOfViewRequested().isEmpty());
    }

    private static CameraFrame turnedAway() {
        return FakeCameras.looking(CELL + PAST_THE_EDGE, INSIDE, INSIDE, 1.0F, 0.0F, 0.0F, FAR_CELLS,
                FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private TreeNode meshedRoot(long key) {
        TreeNode root = nodes.root(key);
        root.meshed(TestMeshes.summary(key, ALL_OCTANTS));
        return root;
    }

    private List<MeshSummary> meshedChildren(TreeNode parent) {
        List<MeshSummary> meshes = new ArrayList<>();
        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            TreeNode child = nodes.child(parent, octant);
            MeshSummary mesh = TestMeshes.summary(child.key(), OccupancyMask.EMPTY);
            child.meshed(mesh);
            meshes.add(mesh);
        }

        return meshes;
    }
}
