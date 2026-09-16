package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ColumnCoverageTest {
    private static final int GRID = ColumnCoverage.GRID_SIDE * ColumnCoverage.GRID_SIDE;

    @Test
    void aLevelZeroColumnIsCoveredByTheChunkItStandsIn() {
        ColumnCoverage coverage = new ColumnCoverage(chunk -> { });
        coverage.cover(0, 0);

        boolean[] grid = filled(coverage, CellKey.pack(0, 0, 0, 0));

        assertTrue(grid[ColumnCoverage.index(0, 0)]);
        assertTrue(grid[ColumnCoverage.index(15, 15)]);
        assertFalse(grid[ColumnCoverage.index(16, 0)]);
        assertFalse(grid[ColumnCoverage.index(0, 16)]);
        assertFalse(grid[ColumnCoverage.index(-1, 0)]);
    }

    @Test
    void aTopLevelVoxelColumnIsExactlyOneChunk() {
        ColumnCoverage coverage = new ColumnCoverage(chunk -> { });
        coverage.cover(3, 5);

        boolean[] grid = filled(coverage, CellKey.pack(DetailLevel.MAX, 0, 0, 0));

        assertTrue(grid[ColumnCoverage.index(3, 5)]);
        assertFalse(grid[ColumnCoverage.index(4, 5)]);
        assertFalse(grid[ColumnCoverage.index(3, 4)]);
    }

    @Test
    void aColumnOutsideTheCellReadsTheChunkAcrossTheBoundary() {
        ColumnCoverage coverage = new ColumnCoverage(chunk -> { });
        coverage.cover(-1, 0);

        boolean[] fine = filled(coverage, CellKey.pack(0, 0, 0, 0));
        boolean[] coarser = filled(coverage, CellKey.pack(1, 0, 0, 0));

        assertTrue(fine[ColumnCoverage.index(-1, 3)]);
        assertFalse(fine[ColumnCoverage.index(0, 3)]);
        assertTrue(coarser[ColumnCoverage.index(-1, 3)]);
    }

    @Test
    void aLoadedChunkIsCoveredAndNotCoveredAgain() {
        ColumnCoverage coverage = new ColumnCoverage(chunk -> { });
        coverage.load(ColumnCoverage.pack(2, -3));

        assertFalse(coverage.cover(2, -3));
        assertTrue(filled(coverage, CellKey.pack(0, 1, 0, -2))[ColumnCoverage.index(0, 16)]);
        assertFalse(filled(coverage, CellKey.pack(0, 1, 0, -2))[ColumnCoverage.index(0, 15)]);
    }

    @Test
    void persistingHandsThePackedChunkToTheStore() {
        List<Long> persisted = new ArrayList<>();
        ColumnCoverage coverage = new ColumnCoverage(persisted::add);

        assertTrue(coverage.cover(-5, 7));
        coverage.persist(-5, 7);

        assertEquals(List.of(((long) -5 << 32) | 7L), persisted);
    }

    @Test
    void everythingCoversEveryColumn() {
        boolean[] grid = filled(ColumnCoverage.everything(), CellKey.pack(2, -9, 0, 4));

        for (boolean column : grid) {
            assertTrue(column);
        }
    }

    private static boolean[] filled(ColumnCoverage coverage, long key) {
        boolean[] grid = new boolean[GRID];
        coverage.fill(key, grid);
        return grid;
    }
}
