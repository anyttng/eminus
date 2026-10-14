package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;

import org.junit.jupiter.api.Test;

class TreeDispatchTest {
    private static final int CAPACITY = 64;
    private static final int FREE = 1000;
    private static final float PRIORITY = 1.0F;
    private static final long ROOT = CellKey.pack(DetailLevel.MAX, 0, 0, 0);

    private final FakeBuilds builds = new FakeBuilds();
    private final TreeDispatch dispatch =
            new TreeDispatch(builds, new TreeExtent(new CellFrame(0), 1, DetailLevel.MIN));
    private final NodeTable nodes = new NodeTable(CAPACITY);
    private final TreeNode root = nodes.root(ROOT);
    private final TreeNode child = nodes.child(root, 0);

    @Test
    void aFirstBuildOfAChildShrinksTheBudgetUntilItSettles() {
        dispatch.build(child, PRIORITY, false);
        assertEquals(RequestBudget.MAX_PER_WALK - 1, dispatch.budget(FREE));

        dispatch.settle(child);
        assertEquals(RequestBudget.MAX_PER_WALK, dispatch.budget(FREE));
    }

    @Test
    void aRebuildOfAChildAlreadyDrawnLeavesTheBudgetWhole() {
        child.meshed(TestMeshes.summary(child.key(), OccupancyMask.EMPTY));

        dispatch.build(child, PRIORITY, false);
        assertEquals(RequestBudget.MAX_PER_WALK, dispatch.budget(FREE));

        dispatch.settle(child);
        assertEquals(RequestBudget.MAX_PER_WALK, dispatch.budget(FREE));
    }

    @Test
    void aRootBuildNeverCounts() {
        dispatch.build(root, PRIORITY, false);

        assertEquals(RequestBudget.MAX_PER_WALK, dispatch.budget(FREE));
    }

    @Test
    void aFirstBuildOutOfViewShrinksOnlyTheOutOfViewBudget() {
        dispatch.build(child, PRIORITY, true);

        assertEquals(RequestBudget.MAX_PER_WALK, dispatch.budget(FREE));
        assertEquals(RequestBudget.MAX_PER_WALK - 1, dispatch.outOfViewBudget(FREE));
    }
}
