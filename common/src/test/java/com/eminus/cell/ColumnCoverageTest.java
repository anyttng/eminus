package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ColumnCoverageTest {
    private static final int GRID = ColumnCoverage.GRID_SIDE * ColumnCoverage.GRID_SIDE;
    private static final int RAISED = DetailLevel.MIN + 1;

    @Test
    void aLevelZeroColumnIsCoveredByTheChunkItStandsIn() {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
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
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        coverage.cover(3, 5);

        boolean[] grid = filled(coverage, CellKey.pack(DetailLevel.MAX, 0, 0, 0));

        assertTrue(grid[ColumnCoverage.index(3, 5)]);
        assertFalse(grid[ColumnCoverage.index(4, 5)]);
        assertFalse(grid[ColumnCoverage.index(3, 4)]);
    }

    @Test
    void aColumnOutsideTheCellReadsTheChunkAcrossTheBoundary() {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        coverage.cover(-1, 0);

        boolean[] fine = filled(coverage, CellKey.pack(0, 0, 0, 0));
        boolean[] coarser = filled(coverage, CellKey.pack(1, 0, 0, 0));

        assertTrue(fine[ColumnCoverage.index(-1, 3)]);
        assertFalse(fine[ColumnCoverage.index(0, 3)]);
        assertTrue(coarser[ColumnCoverage.index(-1, 3)]);
    }

    @Test
    void aLoadedChunkIsCoveredAndNotCoveredAgain() {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        coverage.load(ColumnCoverage.pack(2, -3), DetailLevel.MIN);

        assertFalse(coverage.cover(2, -3));
        assertTrue(filled(coverage, CellKey.pack(0, 1, 0, -2))[ColumnCoverage.index(0, 16)]);
        assertFalse(filled(coverage, CellKey.pack(0, 1, 0, -2))[ColumnCoverage.index(0, 15)]);
    }

    @Test
    void persistingHandsThePackedChunkAndTheStoredLevelToTheStore() {
        List<String> persisted = new ArrayList<>();
        ColumnCoverage coverage = new ColumnCoverage(RAISED, (chunk, lowestLevel) -> persisted.add(chunk + "@"
                + lowestLevel));

        assertTrue(coverage.cover(-5, 7));
        coverage.persist(-5, 7);

        assertEquals(List.of(((-5L << 32) | 7L) + "@" + RAISED), persisted);
    }

    @Test
    void aColumnLoadedFromALevelAboveTheStoredOneIsCoveredOnlyFromThere() {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        coverage.load(ColumnCoverage.pack(0, 0), RAISED);

        assertFalse(coverage.covers(0, 0));
        assertFalse(filled(coverage, CellKey.pack(DetailLevel.MIN, 0, 0, 0))[ColumnCoverage.index(0, 0)]);
        assertTrue(filled(coverage, CellKey.pack(RAISED, 0, 0, 0))[ColumnCoverage.index(0, 0)]);
    }

    @Test
    void coveringAColumnLoadedAboveBringsItDownToTheStoredLevel() {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        coverage.load(ColumnCoverage.pack(0, 0), RAISED);

        assertTrue(coverage.cover(0, 0));
        assertTrue(coverage.covers(0, 0));
        assertTrue(filled(coverage, CellKey.pack(DetailLevel.MIN, 0, 0, 0))[ColumnCoverage.index(0, 0)]);
    }

    @Test
    void aCellMissesColumnsItsParentHoldsUntilTheyAreCoveredAtItsLevel() {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        coverage.load(ColumnCoverage.pack(0, 0), DetailLevel.MIN);
        coverage.load(ColumnCoverage.pack(1, 0), RAISED);
        long finest = CellKey.pack(DetailLevel.MIN, 0, 0, 0);

        assertTrue(coverage.missingColumns(finest));
        assertFalse(coverage.missingColumns(CellKey.pack(RAISED, 0, 0, 0)));

        coverage.cover(1, 0);

        assertFalse(coverage.missingColumns(finest));
    }

    @Test
    void aColumnNeverCoveredIsNotMissingFromACellWhoseParentLacksItToo() {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        coverage.load(ColumnCoverage.pack(0, 0), DetailLevel.MIN);

        assertFalse(coverage.missingColumns(CellKey.pack(DetailLevel.MIN, 0, 0, 0)));
        assertFalse(ColumnCoverage.everything().missingColumns(CellKey.pack(DetailLevel.MIN, 0, 0, 0)));
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
