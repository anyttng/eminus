package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;
import com.eminus.mesh.MeshSummary;

import org.junit.jupiter.api.Test;

class TreeTraversalZoomTest {
    private static final int CELL = DetailLevel.blocksPerCell(DetailLevel.MAX);
    private static final double INSIDE = CELL / 2.0;
    private static final double BEHIND = -1_000.0;
    private static final int FAR_CELLS = 4;
    private static final int ALL_OCTANTS = 0xFF;
    private static final int LEAF_OCCUPANCY = 0b1;
    private static final int BUDGET = 8;
    private static final int NO_OUT_OF_VIEW = 0;
    private static final long WALK = 7L;
    private static final float HALF_THRESHOLD = 0.5F;
    private static final float SPYGLASS_ZOOM = 10.0F;
    private static final int AXIS_CELL = 2;
    private static final int ASIDE_CELL = 1;

    private final NodeTable nodes = new NodeTable(NodeTable.CAPACITY);
    private final TreeTraversal traversal = new TreeTraversal(nodes,
            new TreeExtent(new CellFrame(0), 1, DetailLevel.MAX - 1), new TreeHorizon());
    private final long rootKey = CellKey.pack(DetailLevel.MAX, 0, 0, 0);

    @Test
    void underAZoomANodeTheZoomRefinesDrawsItselfUntilEveryChildLands() {
        TreeNode root = nodes.root(rootKey);
        root.meshed(TestMeshes.summary(rootKey, ALL_OCTANTS));
        CameraFrame set = FakeCameras.everything(BEHIND, INSIDE, INSIDE, FAR_CELLS,
                (float) (FakeCameras.THRESHOLD_PIXELS * -BEHIND / CELL * HALF_THRESHOLD));
        CameraFrame zoomed = FakeCameras.zoomed(set, set.pixelsPerBlock() * SPYGLASS_ZOOM);

        assertEquals(List.of(root.mesh()), listed(zoomed, WALK).meshes());
        List<TreeNode> children = List.copyOf(traversal.requested());
        assertEquals(OccupancyMask.OCTANTS, children.size());

        children.getFirst().meshed(TestMeshes.summary(children.getFirst().key(), LEAF_OCCUPANCY));
        assertEquals(List.of(root.mesh()), listed(zoomed, WALK + 1).meshes());

        for (TreeNode child : children) {
            child.meshed(TestMeshes.summary(child.key(), LEAF_OCCUPANCY));
        }

        assertEquals(children.stream().map(TreeNode::key).sorted().toList(),
                listed(zoomed, WALK + 2).meshes().stream().map(MeshSummary::key).sorted().toList());
    }

    @Test
    void underAZoomTheBudgetGoesFirstToTheNodeOnTheCrosshairOverANearerOneAside() {
        TreeNode onAxis = meshedRoot(CellKey.pack(DetailLevel.MAX, AXIS_CELL, 0, 0));
        meshedRoot(CellKey.pack(DetailLevel.MAX, 0, 0, ASIDE_CELL));

        traversal.walk(nodes.roots(), FakeCameras.zoomed(alongX(), FakeCameras.CLOSE_PIXELS_PER_BLOCK * SPYGLASS_ZOOM),
                BUDGET, NO_OUT_OF_VIEW, WALK);

        assertEquals(OccupancyMask.OCTANTS, traversal.requested().size());
        for (TreeNode child : traversal.requested()) {
            assertSame(onAxis, child.parent());
        }
    }

    @Test
    void withoutAZoomTheBudgetGoesFirstToTheNearerNodeAside() {
        meshedRoot(CellKey.pack(DetailLevel.MAX, AXIS_CELL, 0, 0));
        TreeNode aside = meshedRoot(CellKey.pack(DetailLevel.MAX, 0, 0, ASIDE_CELL));

        traversal.walk(nodes.roots(), alongX(), BUDGET, NO_OUT_OF_VIEW, WALK);

        assertEquals(OccupancyMask.OCTANTS, traversal.requested().size());
        for (TreeNode child : traversal.requested()) {
            assertSame(aside, child.parent());
        }
    }

    private static CameraFrame alongX() {
        return FakeCameras.looking(-CELL, INSIDE, INSIDE, 1.0F, 0.0F, 0.0F, FAR_CELLS,
                FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private TreeNode meshedRoot(long key) {
        TreeNode root = nodes.root(key);
        root.meshed(TestMeshes.summary(key, ALL_OCTANTS));
        return root;
    }

    private RenderList listed(CameraFrame camera, long walk) {
        traversal.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, walk);
        return traversal.list(camera);
    }
}
