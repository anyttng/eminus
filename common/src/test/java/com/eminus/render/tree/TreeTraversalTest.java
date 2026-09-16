package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;
import com.eminus.mesh.CellMesh;

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

    private final NodeTable nodes = new NodeTable(NodeTable.CAPACITY);
    private final TreeExtent extent = new TreeExtent(new CellFrame(0), 1, DetailLevel.MIN);
    private final TreeTraversal traversal = new TreeTraversal(nodes, extent);
    private final long rootKey = CellKey.pack(DetailLevel.MAX, 0, 0, 0);

    @Test
    void aNodeOutsideTheFrustumIsAbsentAndUnseen() {
        TreeNode root = meshedRoot(rootKey, OccupancyMask.EMPTY);

        RenderList behind = traversal.walk(nodes.roots(), FakeCameras.looking(BEHIND, INSIDE, INSIDE,
                -1.0F, 0.0F, 0.0F, FAR_CELLS, FakeCameras.FAR_PIXELS_PER_BLOCK), BUDGET, WALK);
        assertTrue(behind.meshes().isEmpty());
        assertEquals(0L, root.lastSeen());

        RenderList ahead = traversal.walk(nodes.roots(), FakeCameras.looking(BEHIND, INSIDE, INSIDE,
                1.0F, 0.0F, 0.0F, FAR_CELLS, FakeCameras.FAR_PIXELS_PER_BLOCK), BUDGET, WALK);
        assertEquals(List.of(root.mesh()), ahead.meshes());
        assertEquals(WALK, root.lastSeen());
    }

    @Test
    void aNodeBeyondTheFarRenderDistanceIsAbsent() {
        TreeNode root = meshedRoot(CellKey.pack(DetailLevel.MAX, FIVE_CELLS, 0, 0), OccupancyMask.EMPTY);

        assertTrue(traversal.walk(nodes.roots(), far(ONE_CELL), BUDGET, WALK).meshes().isEmpty());
        assertEquals(List.of(root.mesh()), traversal.walk(nodes.roots(), far(FIVE_CELLS), BUDGET, WALK).meshes());
    }

    @Test
    void aLargeNodeWithoutChildrenRequestsThemOnceAndDrawsItself() {
        TreeNode root = meshedRoot(rootKey, TWO_OCTANTS);

        RenderList first = traversal.walk(nodes.roots(), inside(), BUDGET, WALK);
        assertEquals(List.of(root.mesh()), first.meshes());
        assertEquals(List.of(CellKey.child(rootKey, 0), CellKey.child(rootKey, 1)),
                traversal.requested().stream().map(TreeNode::key).toList());

        RenderList second = traversal.walk(nodes.roots(), inside(), BUDGET, WALK + 1);
        assertEquals(List.of(root.mesh()), second.meshes());
        assertTrue(traversal.requested().isEmpty());
    }

    @Test
    void requestsNeverExceedTheBudget() {
        meshedRoot(rootKey, ALL_OCTANTS);

        traversal.walk(nodes.roots(), inside(), SMALL_BUDGET, WALK);
        assertEquals(SMALL_BUDGET, traversal.requested().size());

        traversal.walk(nodes.roots(), inside(), SMALL_BUDGET, WALK + 1);
        assertEquals(SMALL_BUDGET, traversal.requested().size());

        traversal.walk(nodes.roots(), inside(), SMALL_BUDGET, WALK + 2);
        assertEquals(OccupancyMask.OCTANTS - 2 * SMALL_BUDGET, traversal.requested().size());

        traversal.walk(nodes.roots(), inside(), SMALL_BUDGET, WALK + 3);
        assertTrue(traversal.requested().isEmpty());
    }

    @Test
    void theChildrenReplaceTheParentOnceEveryOneHasAMesh() {
        TreeNode root = meshedRoot(rootKey, TWO_CORNERS);
        traversal.walk(nodes.roots(), inside(), BUDGET, WALK);
        List<TreeNode> children = List.copyOf(traversal.requested());
        assertEquals(2, children.size());

        CellMesh first = TestMeshes.of(children.get(0).key(), OccupancyMask.EMPTY);
        children.get(0).meshed(first);
        assertEquals(List.of(root.mesh()), traversal.walk(nodes.roots(), inside(), BUDGET, WALK + 1).meshes());

        CellMesh second = TestMeshes.of(children.get(1).key(), OccupancyMask.EMPTY);
        children.get(1).meshed(second);
        assertEquals(List.of(first, second), traversal.walk(nodes.roots(), inside(), BUDGET, WALK + 2).meshes());
    }

    @Test
    void aDescendedNodeKeepsItsReadyChildrenWhenAnOctantFillsIn() {
        TreeNode root = meshedRoot(rootKey, TWO_CORNERS);
        traversal.walk(nodes.roots(), inside(), BUDGET, WALK);
        List<TreeNode> children = List.copyOf(traversal.requested());
        CellMesh first = TestMeshes.of(children.get(0).key(), OccupancyMask.EMPTY);
        CellMesh second = TestMeshes.of(children.get(1).key(), OccupancyMask.EMPTY);
        children.get(0).meshed(first);
        children.get(1).meshed(second);
        assertEquals(List.of(first, second), traversal.walk(nodes.roots(), inside(), BUDGET, WALK + 1).meshes());

        root.meshed(TestMeshes.of(rootKey, CORNERS_AND_BETWEEN));
        RenderList filled = traversal.walk(nodes.roots(), inside(), BUDGET, WALK + 2);

        assertEquals(List.of(CellKey.child(rootKey, BETWEEN_OCTANT)),
                traversal.requested().stream().map(TreeNode::key).toList());
        assertEquals(List.of(first, second), filled.meshes());
    }

    @Test
    void aDescendedNodeThatLosesAChildDrawsItselfUntilTheChildReturns() {
        TreeNode root = meshedRoot(rootKey, TWO_CORNERS);
        traversal.walk(nodes.roots(), inside(), BUDGET, WALK);
        List<TreeNode> children = List.copyOf(traversal.requested());
        children.get(0).meshed(TestMeshes.of(children.get(0).key(), OccupancyMask.EMPTY));
        children.get(1).meshed(TestMeshes.of(children.get(1).key(), OccupancyMask.EMPTY));
        traversal.walk(nodes.roots(), inside(), BUDGET, WALK + 1);

        nodes.remove(children.get(0), removed -> { });

        assertEquals(List.of(root.mesh()), traversal.walk(nodes.roots(), inside(), BUDGET, WALK + 2).meshes());
    }

    @Test
    void aSmallNodeDrawsItselfWithoutRequesting() {
        TreeNode root = meshedRoot(rootKey, ALL_OCTANTS);

        RenderList list = traversal.walk(nodes.roots(),
                FakeCameras.everything(BEHIND, INSIDE, INSIDE, FAR_CELLS, FakeCameras.FAR_PIXELS_PER_BLOCK),
                BUDGET, WALK);

        assertEquals(List.of(root.mesh()), list.meshes());
        assertTrue(traversal.requested().isEmpty());
    }

    @Test
    void nothingSubdividesBelowTheLowestStoredLevel() {
        TreeTraversal capped = new TreeTraversal(nodes, new TreeExtent(new CellFrame(0), 1, LOWEST_IS_TOP));
        TreeNode root = meshedRoot(rootKey, ALL_OCTANTS);

        assertEquals(List.of(root.mesh()), capped.walk(nodes.roots(), inside(), BUDGET, WALK).meshes());
        assertTrue(capped.requested().isEmpty());
    }

    @Test
    void anExhaustedTableRefusesRequestsQuietly() {
        NodeTable tiny = new NodeTable(TINY_TABLE);
        TreeTraversal starved = new TreeTraversal(tiny, extent);
        TreeNode root = tiny.root(rootKey);
        root.meshed(TestMeshes.of(rootKey, ALL_OCTANTS));

        assertEquals(List.of(root.mesh()), starved.walk(tiny.roots(), inside(), BUDGET, WALK).meshes());
        assertTrue(starved.requested().isEmpty());
        assertEquals(0, tiny.free());
    }

    private TreeNode meshedRoot(long key, int occupancy) {
        TreeNode root = nodes.root(key);
        root.meshed(TestMeshes.of(key, occupancy));
        return root;
    }

    private static CameraFrame inside() {
        return FakeCameras.everything(INSIDE, INSIDE, INSIDE, FAR_CELLS, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private static CameraFrame far(int farCells) {
        return FakeCameras.everything(INSIDE, INSIDE, INSIDE, farCells, FakeCameras.FAR_PIXELS_PER_BLOCK);
    }
}
