package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.eminus.settings.FarDistance;

import org.junit.jupiter.api.Test;

class TreeRootsTest {
    private static final int CELL = FarDistance.BLOCKS_PER_TOP_LEVEL_CELL;
    private static final double NEAR_THE_EDGE = CELL - 12.0;
    private static final double MID_CELL = CELL / 2.0;
    private static final double TELEPORT = 2_000.0;
    private static final int ONE_CELL = 1;
    private static final int TWO_CELLS = 2;
    private static final int WIDE = 8;
    private static final int UNIT_DISC = 5;
    private static final int TWO_CELL_DISC = 13;

    private final TreeRoots roots = new TreeRoots();
    private final Recorder columns = new Recorder();

    @Test
    void theFirstUpdateFillsTheDiscNearestColumnFirst() {
        assertTrue(roots.update(MID_CELL, MID_CELL, ONE_CELL, columns));

        assertEquals(UNIT_DISC, columns.added.size());
        assertEquals(TreeRoots.column(0, 0), columns.added.get(0));
        assertTrue(columns.removed.isEmpty());
        assertEquals(UNIT_DISC, roots.columns());
        assertFalse(roots.pending());
    }

    @Test
    void aMoveOf127BlocksChangesNothing() {
        roots.update(NEAR_THE_EDGE, MID_CELL, ONE_CELL, columns);
        columns.clear();

        assertFalse(roots.update(NEAR_THE_EDGE + TreeRoots.MOVE_BLOCKS - 1, MID_CELL, ONE_CELL, columns));
        assertTrue(columns.added.isEmpty());
        assertTrue(columns.removed.isEmpty());
    }

    @Test
    void aMoveOf128BlocksAddsAndRemovesTheCrescentsOnly() {
        roots.update(NEAR_THE_EDGE, MID_CELL, ONE_CELL, columns);
        columns.clear();

        assertTrue(roots.update(NEAR_THE_EDGE + TreeRoots.MOVE_BLOCKS, MID_CELL, ONE_CELL, columns));

        assertEquals(Set.of(TreeRoots.column(-1, 0), TreeRoots.column(0, 1), TreeRoots.column(0, -1)),
                new HashSet<>(columns.removed));
        assertEquals(Set.of(TreeRoots.column(2, 0), TreeRoots.column(1, 1), TreeRoots.column(1, -1)),
                new HashSet<>(columns.added));
        assertEquals(UNIT_DISC, roots.columns());
    }

    @Test
    void aTeleportReplacesTheDisc() {
        roots.update(MID_CELL, MID_CELL, ONE_CELL, columns);
        Set<Long> before = new HashSet<>(columns.added);
        columns.clear();

        assertTrue(roots.update(MID_CELL + TELEPORT, MID_CELL, ONE_CELL, columns));

        assertEquals(before, new HashSet<>(columns.removed));
        assertEquals(UNIT_DISC, columns.added.size());
        for (long column : columns.added) {
            assertFalse(before.contains(column));
        }
    }

    @Test
    void columnsArriveWithinThePerUpdateBudget() {
        int updates = 0;

        do {
            columns.clear();
            roots.update(MID_CELL, MID_CELL, WIDE, columns);
            updates++;
            assertTrue(columns.added.size() <= TreeRoots.COLUMNS_PER_UPDATE);
        } while (roots.pending());

        assertEquals(discSize(WIDE), roots.columns());
        assertEquals(Math.ceilDiv(discSize(WIDE), TreeRoots.COLUMNS_PER_UPDATE), updates);
    }

    @Test
    void aWiderRadiusAddsTheOuterRingWithoutMoving() {
        roots.update(MID_CELL, MID_CELL, ONE_CELL, columns);
        columns.clear();

        assertTrue(roots.update(MID_CELL, MID_CELL, TWO_CELLS, columns));

        assertEquals(TWO_CELL_DISC - UNIT_DISC, columns.added.size());
        assertTrue(columns.removed.isEmpty());
        assertEquals(TWO_CELL_DISC, roots.columns());
    }

    private static int discSize(int radius) {
        int count = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    count++;
                }
            }
        }

        return count;
    }

    private static final class Recorder implements TreeRoots.Columns {
        private final List<Long> added = new ArrayList<>();
        private final List<Long> removed = new ArrayList<>();

        @Override
        public void added(int cellX, int cellZ) {
            added.add(TreeRoots.column(cellX, cellZ));
        }

        @Override
        public void removed(int cellX, int cellZ) {
            removed.add(TreeRoots.column(cellX, cellZ));
        }

        void clear() {
            added.clear();
            removed.clear();
        }
    }
}
