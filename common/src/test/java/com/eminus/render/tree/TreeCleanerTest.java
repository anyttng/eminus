package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;

import org.junit.jupiter.api.Test;

class TreeCleanerTest {
    private static final int FOUR_OCTANTS = 0b1111;
    private static final long RECENT = 5L;
    private static final long OLDEST = 1L;
    private static final long OLD = 3L;
    private static final long NEXT_WALK = RECENT + 1;

    private final NodeTable nodes = new NodeTable(NodeTable.CAPACITY);
    private final TreeCleaner cleaner = new TreeCleaner();
    private final TreeNode root = nodes.root(CellKey.pack(DetailLevel.MAX, 0, 0, 0));
    private final TreeNode recent = child(0, RECENT);
    private final TreeNode oldest = child(1, OLDEST);
    private final TreeNode old = child(2, OLD);
    private final TreeNode unmeshed = nodes.child(root, 3);

    TreeCleanerTest() {
        root.meshed(TestMeshes.of(root.key(), FOUR_OCTANTS));
        root.seen(OLDEST);
    }

    @Test
    void nothingIsEvictedWithoutPressure() {
        assertTrue(cleaner.pick(nodes.all(), false, NEXT_WALK).isEmpty());
    }

    @Test
    void underPressureTheLeastRecentlySeenMeshedNonRootsGoFirst() {
        assertEquals(List.of(oldest, old, recent), cleaner.pick(nodes.all(), true, NEXT_WALK));
        assertNull(unmeshed.mesh());
    }

    @Test
    void underPressureNothingTheWalkUsedIsEvicted() {
        assertEquals(List.of(oldest, old), cleaner.pick(nodes.all(), true, RECENT));
        assertTrue(cleaner.pick(nodes.all(), true, OLDEST).isEmpty());
    }

    private TreeNode child(int octant, long seen) {
        TreeNode child = nodes.child(root, octant);
        child.meshed(TestMeshes.of(child.key(), OccupancyMask.EMPTY));
        child.seen(seen);
        return child;
    }
}
