package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.mesh.MeshSummary;

import org.junit.jupiter.api.Test;

class TreeTraversalTargetLevelTest {
    private static final double INSIDE = DetailLevel.blocksPerCell(DetailLevel.MAX) / 2.0;
    private static final int FAR_CELLS = 4;
    private static final int BUDGET = 8;
    private static final int NO_OUT_OF_VIEW = 0;
    private static final long WALK = 7L;
    private static final int FIRST_OCTANT = 0b1;
    private static final long FIRST_TWO_GRANDCHILDREN = 0b11L;
    private static final int TOP_OCTANT = 4;
    private static final double PREFETCH_DISTANCE = 1_000.0;
    private static final float PREFETCH_PIXELS_PER_BLOCK = 112.5F;
    private static final float FRONT_PIXELS_PER_BLOCK = 0.01F;
    private static final long FAR_ROOT = CellKey.pack(DetailLevel.MAX, 3, 0, 0);
    private static final long ABOVE_ROOT = CellKey.pack(DetailLevel.MAX, 0, 1, 0);
    private static final int AHEAD = 1;
    private static final int AHEAD_OCTANT = 1 << AHEAD;
    private static final int BOTTOM_AND_TOP = (1 << TOP_OCTANT) | FIRST_OCTANT;
    private static final int TWO_BELOW_THE_ROOT = DetailLevel.MAX - 2;
    private static final double BEFORE_THE_SECOND_ROOT = DetailLevel.blocksPerCell(DetailLevel.MAX) - 1.0;
    private static final float BEHIND_PIXELS_PER_BLOCK = 1.0F;
    private static final long BUILD_MILLIS = 5L;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final NodeTable nodes = new NodeTable(NodeTable.CAPACITY);
    private final TreeTraversal traversal = new TreeTraversal(nodes,
            new TreeExtent(new CellFrame(0), 1, TWO_BELOW_THE_ROOT), new TreeHorizon());
    private final TreeTraversal leaves = new TreeTraversal(nodes,
            new TreeExtent(new CellFrame(0), 1, DetailLevel.MAX - 1), new TreeHorizon());
    private final long rootKey = CellKey.pack(DetailLevel.MAX, 0, 0, 0);

    @Test
    void aPlaceDrawsNothingUntilTheLevelTheRuleWantsLandsThere() {
        TreeNode root = nodes.root(rootKey);
        root.meshed(TestMeshes.summary(rootKey, FIRST_OCTANT));

        assertTrue(listed(WALK).meshes().isEmpty());
        TreeNode child = only(traversal.requested());

        child.meshed(TestMeshes.summary(child.key(), FIRST_OCTANT));
        assertTrue(listed(WALK + 1).meshes().isEmpty());
        TreeNode grandchild = only(traversal.requested());
        assertEquals(CellKey.child(child.key(), 0), grandchild.key());

        MeshSummary finest = TestMeshes.summary(grandchild.key(), FIRST_OCTANT);
        grandchild.meshed(finest);
        assertEquals(List.of(finest), listed(WALK + 2).meshes());
    }

    @Test
    void aChildTheRuleRefinesComesWithTheGrandchildrenItsParentMarksOccupied() {
        TreeNode root = nodes.root(rootKey);
        root.meshed(TestMeshes.summary(rootKey, FIRST_OCTANT, FIRST_TWO_GRANDCHILDREN));

        listed(WALK);

        long child = CellKey.child(rootKey, 0);
        assertEquals(List.of(child, CellKey.child(child, 0), CellKey.child(child, 1)),
                traversal.requested().stream().map(TreeNode::key).toList());
    }

    @Test
    void aCellWaitsUntilTheCellAboveItInItsColumnIsDrawn() {
        TreeNode root = nodes.root(rootKey);
        root.meshed(TestMeshes.summary(rootKey, BOTTOM_AND_TOP));
        CameraFrame camera = inside();
        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK);
        TreeNode bottom = root.child(0);
        TreeNode top = root.child(TOP_OCTANT);

        MeshSummary below = TestMeshes.summary(bottom.key(), FIRST_OCTANT);
        bottom.meshed(below);
        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK + 1);
        assertTrue(leaves.list(camera).meshes().isEmpty());

        MeshSummary above = TestMeshes.summary(top.key(), FIRST_OCTANT);
        top.meshed(above);
        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK + 2);
        assertEquals(List.of(below, above), leaves.list(camera).meshes());
    }

    @Test
    void aNodeNearingTheSizeRuleRequestsItsChildrenAndStillDrawsItself() {
        TreeNode root = nodes.root(rootKey);
        root.meshed(TestMeshes.summary(rootKey, FIRST_OCTANT));
        CameraFrame camera = FakeCameras.everything(-PREFETCH_DISTANCE, INSIDE, INSIDE, FAR_CELLS,
                PREFETCH_PIXELS_PER_BLOCK);

        traversal.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK);

        assertEquals(List.of(root.mesh()), traversal.list(camera).meshes());
        assertEquals(List.of(CellKey.child(rootKey, 0)), traversal.requested().stream().map(TreeNode::key).toList());
    }

    @Test
    void aChildRequestedAheadOfTheSizeRuleIsSeenByEveryWalkThatKeepsItsParentNearTheRule() {
        TreeNode root = nodes.root(rootKey);
        root.meshed(TestMeshes.summary(rootKey, FIRST_OCTANT));
        CameraFrame camera = FakeCameras.everything(-PREFETCH_DISTANCE, INSIDE, INSIDE, FAR_CELLS,
                PREFETCH_PIXELS_PER_BLOCK);
        traversal.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK);
        TreeNode child = only(traversal.requested());

        traversal.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK + 1);

        assertEquals(WALK + 1, child.lastSeen());
    }

    @Test
    void onArrivalNothingPastTheNearestEmptyPlaceShowsUntilItFillsAndWhatShowedStays() {
        TreeNode near = nodes.root(rootKey);
        near.meshed(TestMeshes.summary(rootKey, FIRST_OCTANT));
        TreeNode far = nodes.root(FAR_ROOT);
        far.meshed(TestMeshes.summary(FAR_ROOT, FIRST_OCTANT));
        CameraFrame camera = FakeCameras.everything(INSIDE, INSIDE, INSIDE, FAR_CELLS, FRONT_PIXELS_PER_BLOCK);

        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK);
        assertTrue(leaves.list(camera).meshes().isEmpty());

        TreeNode child = near.child(0);
        MeshSummary landed = TestMeshes.summary(child.key(), FIRST_OCTANT);
        child.meshed(landed);
        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK + 1);
        assertEquals(List.of(far.mesh(), landed), leaves.list(camera).meshes());

        nodes.remove(child, removed -> { });
        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK + 2);
        assertEquals(List.of(far.mesh()), leaves.list(camera).meshes());
    }

    @Test
    void outOfViewNothingPastTheNearestEmptyPlaceShowsSoATurnFindsNoFarCellOverAnEmptyOne() {
        long behindKey = CellKey.pack(DetailLevel.MAX, 1, 0, 0);
        TreeNode behind = nodes.root(behindKey);
        behind.meshed(TestMeshes.summary(behindKey, FIRST_OCTANT));
        TreeNode far = nodes.root(FAR_ROOT);
        far.meshed(TestMeshes.summary(FAR_ROOT, FIRST_OCTANT));
        CameraFrame camera = FakeCameras.looking(BEFORE_THE_SECOND_ROOT, INSIDE, INSIDE, -1.0F, 0.0F, 0.0F,
                FAR_CELLS, BEHIND_PIXELS_PER_BLOCK);

        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK);
        assertTrue(leaves.list(camera).meshes().isEmpty());

        TreeNode child = nodes.child(behind, 0);
        MeshSummary landed = TestMeshes.summary(child.key(), FIRST_OCTANT);
        child.meshed(landed);
        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK + 1);
        assertEquals(List.of(far.mesh(), landed), leaves.list(camera).meshes());
    }

    @Test
    void theFrontNamesTheNearestEmptyPlaceItsBuildAndWhatItHides() {
        TreeNode near = nodes.root(rootKey);
        near.meshed(TestMeshes.summary(rootKey, FIRST_OCTANT));
        TreeNode far = nodes.root(FAR_ROOT);
        far.meshed(TestMeshes.summary(FAR_ROOT, FIRST_OCTANT));
        CameraFrame camera = FakeCameras.everything(INSIDE, INSIDE, INSIDE, FAR_CELLS, FRONT_PIXELS_PER_BLOCK);

        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK);
        leaves.list(camera);
        assertEquals(new FrontState(0, DetailLevel.MAX - 1, 0, 0, 0, FrontState.UNREQUESTED, 0L, 1, 0, 0),
                leaves.front(0L));

        TreeNode child = near.child(0);
        child.startBuild(WALK, false, true);
        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK + 1);
        leaves.list(camera);
        assertEquals(new FrontState(0, DetailLevel.MAX - 1, 0, 0, 0, FrontState.BUILDING, BUILD_MILLIS, 1, 0, 0),
                leaves.front(child.dispatchedAt() + BUILD_MILLIS * NANOS_PER_MILLI));
    }

    @Test
    void aCellInViewHiddenUnderAPlaceWaitingOutOfViewIsCountedApart() {
        TreeNode below = nodes.root(rootKey);
        below.meshed(TestMeshes.summary(rootKey, AHEAD_OCTANT));
        TreeNode above = nodes.root(ABOVE_ROOT);
        above.meshed(TestMeshes.summary(ABOVE_ROOT, AHEAD_OCTANT));
        TreeNode ground = nodes.child(below, AHEAD);
        MeshSummary landed = TestMeshes.summary(ground.key(), FIRST_OCTANT);
        ground.meshed(landed);
        CameraFrame camera = FakeCameras.looking(INSIDE, INSIDE, INSIDE, 1.0F, 0.0F, 0.0F, FAR_CELLS,
                FakeCameras.CLOSE_PIXELS_PER_BLOCK);

        leaves.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, WALK);
        RenderList listed = leaves.list(camera);

        assertTrue(listed.meshes().isEmpty());
        assertEquals(new FrontState(FrontState.NO_FRONT, FrontState.NO_FRONT, 0, 0, 0, FrontState.NONE, 0L, 0, 1, 1),
                leaves.front(0L));
    }

    @Test
    void underPressureAPlaceKeepsItsCoarserMeshWhileTheWantedLevelCannotBeRequested() {
        TreeNode root = nodes.root(rootKey);
        root.meshed(TestMeshes.summary(rootKey, FIRST_OCTANT));

        assertEquals(List.of(root.mesh()), listed(FakeCameras.underPressure(inside()), WALK).meshes());
    }

    private RenderList listed(long walk) {
        return listed(inside(), walk);
    }

    private RenderList listed(CameraFrame camera, long walk) {
        traversal.walk(nodes.roots(), camera, BUDGET, NO_OUT_OF_VIEW, walk);
        return traversal.list(camera);
    }

    private static CameraFrame inside() {
        return FakeCameras.everything(INSIDE, INSIDE, INSIDE, FAR_CELLS, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private static TreeNode only(List<TreeNode> requested) {
        assertEquals(1, requested.size());
        return requested.getFirst();
    }
}
