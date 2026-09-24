package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;
import com.eminus.mesh.MeshSummary;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class TreeTraversalTest {
    private static final int CELL = DetailLevel.blocksPerCell(DetailLevel.MAX);
    private static final double INSIDE = CELL / 2.0;
    private static final double BEHIND = -1_000.0;
    private static final int FAR_CELLS = 4;
    private static final int ONE_CELL = 1;
    private static final int FIVE_CELLS = 5;
    private static final int BUDGET = 8;
    private static final int SMALL_BUDGET = 3;
    private static final long WALK = 7L;
    private static final int TWO_OCTANTS = 0b11;
    private static final int TWO_CORNERS = 0b101;
    private static final int BETWEEN_OCTANT = 1;
    private static final int CORNERS_AND_BETWEEN = 0b111;
    private static final int ALL_OCTANTS = 0xFF;
    private static final int LOWEST_IS_TOP = DetailLevel.MAX;
    private static final int TINY_TABLE = 1;
    private static final int NEXT_CELL = 1;
    private static final int EAST_OCTANTS = 0b1010;
    private static final int NO_OUT_OF_VIEW = 0;
    private static final double PAST_THE_EDGE = 32.0;
    private static final int ROOTS_AND_ONE_CHILD = 3;

    private final NodeTable nodes = new NodeTable(NodeTable.CAPACITY);
    private final TreeExtent extent = new TreeExtent(new CellFrame(0), 1, DetailLevel.MIN);
    private final TreeTraversal traversal = new TreeTraversal(nodes, extent);
    private final long rootKey = CellKey.pack(DetailLevel.MAX, 0, 0, 0);

    @Test
    void aNodeOutsideTheFrustumIsAbsentAndUnseen() {
        TreeNode root = meshedRoot(rootKey, OccupancyMask.EMPTY);

        RenderList behind = traversal.walk(nodes.roots(), FakeCameras.looking(BEHIND, INSIDE, INSIDE,
                -1.0F, 0.0F, 0.0F, FAR_CELLS, FakeCameras.FAR_PIXELS_PER_BLOCK), BUDGET, NO_OUT_OF_VIEW, WALK);
        assertTrue(behind.meshes().isEmpty());
        assertEquals(0L, root.lastSeen());

        RenderList ahead = traversal.walk(nodes.roots(), FakeCameras.looking(BEHIND, INSIDE, INSIDE,
                1.0F, 0.0F, 0.0F, FAR_CELLS, FakeCameras.FAR_PIXELS_PER_BLOCK), BUDGET, NO_OUT_OF_VIEW, WALK);
        assertEquals(List.of(root.mesh()), ahead.meshes());
        assertEquals(WALK, root.lastSeen());
    }

    @Test
    void aNodeBeyondTheFarRenderDistanceIsAbsent() {
        TreeNode root = meshedRoot(CellKey.pack(DetailLevel.MAX, FIVE_CELLS, 0, 0), OccupancyMask.EMPTY);

        assertTrue(traversal.walk(nodes.roots(), far(ONE_CELL), BUDGET, NO_OUT_OF_VIEW, WALK).meshes().isEmpty());
        assertEquals(List.of(root.mesh()), traversal.walk(nodes.roots(), far(FIVE_CELLS), BUDGET, NO_OUT_OF_VIEW, WALK).meshes());
    }

    @Test
    void aLargeNodeWithoutChildrenRequestsThemOnceAndDrawsItself() {
        TreeNode root = meshedRoot(rootKey, TWO_OCTANTS);

        RenderList first = traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK);
        assertEquals(List.of(root.mesh()), first.meshes());
        assertEquals(List.of(CellKey.child(rootKey, 0), CellKey.child(rootKey, 1)),
                traversal.requested().stream().map(TreeNode::key).toList());

        RenderList second = traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK + 1);
        assertEquals(List.of(root.mesh()), second.meshes());
        assertTrue(traversal.requested().isEmpty());
    }

    @Test
    void requestsNeverExceedTheBudget() {
        meshedRoot(rootKey, ALL_OCTANTS);

        traversal.walk(nodes.roots(), inside(), SMALL_BUDGET, NO_OUT_OF_VIEW, WALK);
        assertEquals(SMALL_BUDGET, traversal.requested().size());

        traversal.walk(nodes.roots(), inside(), SMALL_BUDGET, NO_OUT_OF_VIEW, WALK + 1);
        assertEquals(SMALL_BUDGET, traversal.requested().size());

        traversal.walk(nodes.roots(), inside(), SMALL_BUDGET, NO_OUT_OF_VIEW, WALK + 2);
        assertEquals(OccupancyMask.OCTANTS - 2 * SMALL_BUDGET, traversal.requested().size());

        traversal.walk(nodes.roots(), inside(), SMALL_BUDGET, NO_OUT_OF_VIEW, WALK + 3);
        assertTrue(traversal.requested().isEmpty());
    }

    @Test
    void theChildrenReplaceTheParentOnceEveryOneHasAMesh() {
        TreeNode root = meshedRoot(rootKey, TWO_CORNERS);
        traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK);
        List<TreeNode> children = List.copyOf(traversal.requested());
        assertEquals(2, children.size());

        MeshSummary first = TestMeshes.summary(children.get(0).key(), OccupancyMask.EMPTY);
        children.get(0).meshed(first);
        assertEquals(List.of(root.mesh()), traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK + 1).meshes());

        MeshSummary second = TestMeshes.summary(children.get(1).key(), OccupancyMask.EMPTY);
        children.get(1).meshed(second);
        assertEquals(List.of(first, second), traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK + 2).meshes());
    }

    @Test
    void theChildrenOfANodeWaitingForTheRestAreSeenByTheWalk() {
        meshedRoot(rootKey, TWO_CORNERS);
        traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK);
        List<TreeNode> children = List.copyOf(traversal.requested());
        children.get(0).meshed(TestMeshes.summary(children.get(0).key(), OccupancyMask.EMPTY));

        traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK + 1);

        assertEquals(WALK + 1, children.get(0).lastSeen());
        assertEquals(WALK + 1, children.get(1).lastSeen());
    }

    @Test
    void theChildrenOfADescendedNodeBeyondTheFarRenderDistanceAreSeenByTheWalk() {
        long edgeKey = CellKey.pack(DetailLevel.MAX, NEXT_CELL, 0, 0);
        meshedRoot(edgeKey, ALL_OCTANTS);
        CameraFrame atEdge = FakeCameras.everything(0.0, INSIDE, INSIDE, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
        traversal.walk(nodes.roots(), atEdge, BUDGET, NO_OUT_OF_VIEW, WALK);
        List<TreeNode> children = List.copyOf(traversal.requested());
        assertEquals(OccupancyMask.OCTANTS, children.size());
        for (TreeNode child : children) {
            child.meshed(TestMeshes.summary(child.key(), OccupancyMask.EMPTY));
        }

        traversal.walk(nodes.roots(), atEdge, BUDGET, NO_OUT_OF_VIEW, WALK + 1);

        for (TreeNode child : children) {
            assertEquals(WALK + 1, child.lastSeen());
        }
    }

    @Test
    void underArenaPressureAWalkRequestsNothingAndIsNotStarved() {
        TreeNode root = meshedRoot(rootKey, ALL_OCTANTS);

        RenderList list = traversal.walk(nodes.roots(), FakeCameras.underPressure(inside()), BUDGET, NO_OUT_OF_VIEW, WALK);

        assertEquals(List.of(root.mesh()), list.meshes());
        assertTrue(traversal.requested().isEmpty());
        assertFalse(traversal.starved());
    }

    @Test
    void aDescendedNodeKeepsItsReadyChildrenWhenAnOctantFillsIn() {
        TreeNode root = meshedRoot(rootKey, TWO_CORNERS);
        traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK);
        List<TreeNode> children = List.copyOf(traversal.requested());
        MeshSummary first = TestMeshes.summary(children.get(0).key(), OccupancyMask.EMPTY);
        MeshSummary second = TestMeshes.summary(children.get(1).key(), OccupancyMask.EMPTY);
        children.get(0).meshed(first);
        children.get(1).meshed(second);
        assertEquals(List.of(first, second), traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK + 1).meshes());

        root.meshed(TestMeshes.summary(rootKey, CORNERS_AND_BETWEEN));
        RenderList filled = traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK + 2);

        assertEquals(List.of(CellKey.child(rootKey, BETWEEN_OCTANT)),
                traversal.requested().stream().map(TreeNode::key).toList());
        assertEquals(List.of(first, second), filled.meshes());
    }

    @Test
    void aDescendedNodeThatLosesAChildDrawsItselfUntilTheChildReturns() {
        TreeNode root = meshedRoot(rootKey, TWO_CORNERS);
        traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK);
        List<TreeNode> children = List.copyOf(traversal.requested());
        children.get(0).meshed(TestMeshes.summary(children.get(0).key(), OccupancyMask.EMPTY));
        children.get(1).meshed(TestMeshes.summary(children.get(1).key(), OccupancyMask.EMPTY));
        traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK + 1);

        nodes.remove(children.get(0), removed -> { });

        assertEquals(List.of(root.mesh()), traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK + 2).meshes());
    }

    @Test
    void theBudgetGoesToTheNodeLargestOnScreenWhateverTheWalkOrder() {
        TreeNode next = meshedRoot(CellKey.pack(DetailLevel.MAX, NEXT_CELL, 0, 0), ALL_OCTANTS);
        TreeNode under = meshedRoot(rootKey, ALL_OCTANTS);
        assertSame(next, nodes.roots().iterator().next());

        traversal.walk(nodes.roots(), inside(), OccupancyMask.OCTANTS, NO_OUT_OF_VIEW, WALK);

        assertEquals(OccupancyMask.OCTANTS, traversal.requested().size());
        for (int index = 0; index < OccupancyMask.OCTANTS; index++) {
            assertSame(under, traversal.requested().get(index).parent());
            assertEquals(ProjectedSize.CONTAINS_CAMERA, traversal.requestedPriority(index));
        }

        assertTrue(traversal.starved());
    }

    @Test
    void aDrawnNodeIsMarkedTowardsEveryNeighbourDrawnAtAnotherLevel() {
        TreeNode split = meshedRoot(rootKey, EAST_OCTANTS);
        long wholeKey = CellKey.pack(DetailLevel.MAX, NEXT_CELL, 0, 0);
        meshedRoot(wholeKey, OccupancyMask.EMPTY);

        RenderList before = traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK);
        assertEquals(RenderList.NO_BORDER_FACES, before.borderFaces(wholeKey));
        List<TreeNode> children = List.copyOf(traversal.requested());
        for (TreeNode child : children) {
            child.meshed(TestMeshes.summary(child.key(), OccupancyMask.EMPTY));
        }

        RenderList after = traversal.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK + 1);

        assertFalse(after.meshes().contains(split.mesh()));
        assertEquals(1 << Direction.WEST.ordinal(), after.borderFaces(wholeKey));
        for (TreeNode child : children) {
            assertEquals(1 << Direction.EAST.ordinal(), after.borderFaces(child.key()));
        }
    }

    @Test
    void aSmallNodeDrawsItselfWithoutRequesting() {
        TreeNode root = meshedRoot(rootKey, ALL_OCTANTS);

        RenderList list = traversal.walk(nodes.roots(),
                FakeCameras.everything(BEHIND, INSIDE, INSIDE, FAR_CELLS, FakeCameras.FAR_PIXELS_PER_BLOCK),
                BUDGET, NO_OUT_OF_VIEW, WALK);

        assertEquals(List.of(root.mesh()), list.meshes());
        assertTrue(traversal.requested().isEmpty());
    }

    @Test
    void nothingSubdividesBelowTheLowestStoredLevel() {
        TreeTraversal capped = new TreeTraversal(nodes, new TreeExtent(new CellFrame(0), 1, LOWEST_IS_TOP));
        TreeNode root = meshedRoot(rootKey, ALL_OCTANTS);

        assertEquals(List.of(root.mesh()), capped.walk(nodes.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK).meshes());
        assertTrue(capped.requested().isEmpty());
    }

    @Test
    void anExhaustedTableRefusesRequestsQuietly() {
        NodeTable tiny = new NodeTable(TINY_TABLE);
        TreeTraversal starved = new TreeTraversal(tiny, extent);
        TreeNode root = tiny.root(rootKey);
        root.meshed(TestMeshes.summary(rootKey, ALL_OCTANTS));

        assertEquals(List.of(root.mesh()), starved.walk(tiny.roots(), inside(), BUDGET, NO_OUT_OF_VIEW, WALK).meshes());
        assertTrue(starved.requested().isEmpty());
        assertEquals(0, tiny.free());
    }

    @Test
    void anOutOfViewNodeWaitsUntilEveryInViewCandidateIsServed() {
        meshedRoot(rootKey, ALL_OCTANTS);
        meshedRoot(aheadKey(), ALL_OCTANTS);

        traversal.walk(nodes.roots(), turnedAway(), SMALL_BUDGET, BUDGET + SMALL_BUDGET, WALK);

        assertEquals(SMALL_BUDGET, traversal.requested().size());
        assertTrue(traversal.starved());
        assertTrue(traversal.outOfViewRequested().isEmpty());
    }

    @Test
    void anOutOfViewNodeTakesOnlyTheBudgetTheInViewListLeft() {
        TreeNode behind = meshedRoot(rootKey, ALL_OCTANTS);
        TreeNode ahead = meshedRoot(aheadKey(), ALL_OCTANTS);

        traversal.walk(nodes.roots(), turnedAway(), BUDGET + SMALL_BUDGET, BUDGET + SMALL_BUDGET, WALK);

        assertEquals(OccupancyMask.OCTANTS, traversal.requested().size());
        for (TreeNode child : traversal.requested()) {
            assertSame(ahead, child.parent());
        }

        assertEquals(SMALL_BUDGET, traversal.outOfViewRequested().size());
        for (int index = 0; index < SMALL_BUDGET; index++) {
            assertSame(behind, traversal.outOfViewRequested().get(index).parent());
            assertTrue(traversal.outOfViewSize(index) > FakeCameras.THRESHOLD_PIXELS);
        }

        assertFalse(traversal.starved());
    }

    @Test
    void withNoBudgetLeftNothingOutOfViewIsRequested() {
        meshedRoot(rootKey, ALL_OCTANTS);
        meshedRoot(aheadKey(), ALL_OCTANTS);

        traversal.walk(nodes.roots(), turnedAway(), BUDGET, BUDGET, WALK);
        assertEquals(OccupancyMask.OCTANTS, traversal.requested().size());
        assertTrue(traversal.outOfViewRequested().isEmpty());

        traversal.walk(nodes.roots(), turnedAway(), BUDGET, NO_OUT_OF_VIEW, WALK + 1);
        assertTrue(traversal.outOfViewRequested().isEmpty());
    }

    @Test
    void theOutOfViewPassDescendsWithoutMarkingAnyNodeSeen() {
        TreeNode behind = meshedRoot(rootKey, ALL_OCTANTS);
        meshedRoot(aheadKey(), OccupancyMask.EMPTY);
        traversal.walk(nodes.roots(), turnedAway(), BUDGET, BUDGET, WALK);
        List<TreeNode> children = List.copyOf(traversal.outOfViewRequested());
        assertEquals(OccupancyMask.OCTANTS, children.size());
        for (TreeNode child : children) {
            child.meshed(TestMeshes.summary(child.key(), ALL_OCTANTS));
        }

        traversal.walk(nodes.roots(), turnedAway(), BUDGET, BUDGET, WALK + 1);

        assertEquals(BUDGET, traversal.outOfViewRequested().size());
        for (TreeNode grandchild : traversal.outOfViewRequested()) {
            assertTrue(children.contains(grandchild.parent()));
        }

        assertEquals(0L, behind.lastSeen());
        for (TreeNode child : children) {
            assertEquals(0L, child.lastSeen());
        }
    }

    @Test
    void aFullTableStopsTheOutOfViewPassWithoutStarving() {
        NodeTable small = new NodeTable(ROOTS_AND_ONE_CHILD);
        TreeTraversal limited = new TreeTraversal(small, extent);
        small.root(rootKey).meshed(TestMeshes.summary(rootKey, ALL_OCTANTS));
        small.root(aheadKey()).meshed(TestMeshes.summary(aheadKey(), OccupancyMask.EMPTY));

        limited.walk(small.roots(), turnedAway(), BUDGET, BUDGET, WALK);

        assertEquals(1, limited.outOfViewRequested().size());
        assertFalse(limited.starved());
    }

    private static long aheadKey() {
        return CellKey.pack(DetailLevel.MAX, NEXT_CELL, 0, 0);
    }

    private static CameraFrame turnedAway() {
        return FakeCameras.looking(CELL + PAST_THE_EDGE, INSIDE, INSIDE, 1.0F, 0.0F, 0.0F, FAR_CELLS,
                FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private TreeNode meshedRoot(long key, int occupancy) {
        TreeNode root = nodes.root(key);
        root.meshed(TestMeshes.summary(key, occupancy));
        return root;
    }

    private static CameraFrame inside() {
        return FakeCameras.everything(INSIDE, INSIDE, INSIDE, FAR_CELLS, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private static CameraFrame far(int farCells) {
        return FakeCameras.everything(INSIDE, INSIDE, INSIDE, farCells, FakeCameras.FAR_PIXELS_PER_BLOCK);
    }
}
