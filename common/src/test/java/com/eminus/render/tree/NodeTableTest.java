package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final float FOCAL_PIXELS = 771.4F;
    private static final int HIGH_PIXELS = 32;
    private static final int MINIMAL_PIXELS = 256;
    private static final int FAR_CELLS = 16;
    private static final int NEAR_FAR_CELLS = 1;
    private static final int WORLD_HEIGHT = 384;
    private static final long HIGH_COLUMNS_BELOW_LEVEL_3 = 7_302L;
    private static final long HIGH_COLUMNS_LEVEL_3 = 3_217L;
    private static final long HIGH_COLUMNS_LEVEL_4 = 804L;
    private static final long CELLS_PER_COLUMN_LEVELS_0_TO_2 = 12 + 6 + 3;
    private static final long CELLS_PER_COLUMN_LEVEL_3 = 2;
    private static final long WHOLE_PERCENT = 100;
    private static final long HIGH_WATER_PERCENT = 85;
    private static final long HIGH_SPHERE_CELLS = HIGH_COLUMNS_BELOW_LEVEL_3 * CELLS_PER_COLUMN_LEVELS_0_TO_2
            + HIGH_COLUMNS_LEVEL_3 * CELLS_PER_COLUMN_LEVEL_3 + HIGH_COLUMNS_LEVEL_4;
    private static final int HIGH_CAPACITY =
            (int) Math.ceilDiv(HIGH_SPHERE_CELLS * WHOLE_PERCENT, HIGH_WATER_PERCENT);
    private static final int OLD_HIGH_WATER_NODES = 55_706;

    private final NodeTable table = new NodeTable(CAPACITY);
    private final List<TreeNode> roots = new ArrayList<>();

    @Test
    void capacityHoldsEveryCellOfTheSphereBelowTheHighWaterMark() {
        assertEquals(HIGH_CAPACITY,
                NodeTable.capacity(DetailLevel.MIN, FOCAL_PIXELS, HIGH_PIXELS, FAR_CELLS, WORLD_HEIGHT));
    }

    @Test
    void capacityNeverFallsBelowTheFixedTable() {
        assertEquals(NodeTable.CAPACITY,
                NodeTable.capacity(DetailLevel.MIN, FOCAL_PIXELS, MINIMAL_PIXELS, NEAR_FAR_CELLS, WORLD_HEIGHT));
    }

    @Test
    void aTableSizedForHighDetailTakesPastTheFixedTablesMarkWithoutPressure() {
        NodeTable sized = new NodeTable(
                NodeTable.capacity(DetailLevel.MIN, FOCAL_PIXELS, HIGH_PIXELS, FAR_CELLS, WORLD_HEIGHT));
        for (int x = 0; x <= OLD_HIGH_WATER_NODES; x++) {
            sized.root(CellKey.pack(DetailLevel.MAX, x, 0, 0));
        }

        assertFalse(sized.pressure());
    }

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
