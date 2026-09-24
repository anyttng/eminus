package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

import org.junit.jupiter.api.Test;

class NodeTableTest {
    private static final int CAPACITY = 20;
    private static final int HIGH_WATER_NODES = 17;
    private static final int LOW_WATER_NODES = 15;
    private static final int ONE_NODE = 1;

    private final NodeTable table = new NodeTable(CAPACITY);
    private final List<TreeNode> roots = new ArrayList<>();

    @Test
    void pressureHoldsFromTheHighWaterMarkUntilBelowTheLowWaterMark() {
        fillTo(HIGH_WATER_NODES - ONE_NODE);
        assertFalse(table.pressure());

        fillTo(HIGH_WATER_NODES);
        assertTrue(table.pressure());

        removeTo(LOW_WATER_NODES);
        assertTrue(table.pressure());

        removeTo(LOW_WATER_NODES - ONE_NODE);
        assertFalse(table.pressure());
    }

    private void fillTo(int size) {
        while (table.size() < size) {
            roots.add(table.root(CellKey.pack(DetailLevel.MAX, roots.size(), 0, 0)));
        }
    }

    private void removeTo(int size) {
        while (table.size() > size) {
            table.remove(roots.removeLast(), removed -> { });
        }
    }
}
