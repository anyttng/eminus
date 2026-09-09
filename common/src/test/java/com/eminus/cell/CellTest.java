package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CellTest {
    private static final long KEY = CellKey.pack(2, -3, 1, 7);
    private static final long STONE = VoxelEntry.pack(17, 4, VoxelEntry.light(0, 0));
    private static final long GRASS = VoxelEntry.pack(9, 4, VoxelEntry.light(15, 0));

    @Test
    void aBlankCellIsAirUnderFullSkyLight() {
        Cell cell = Cell.blank(KEY);

        assertEquals(KEY, cell.key());
        assertTrue(cell.isEmpty());
        assertEquals(OccupancyMask.EMPTY, cell.occupancy());
        assertEquals(1, cell.paletteSize());
        assertEquals(VoxelEntry.AIR, cell.get(0, 0, 0));
        assertEquals(VoxelEntry.AIR, cell.get(31, 31, 31));
        assertEquals(VoxelEntry.MAX_LIGHT, VoxelEntry.skyLight(cell.get(5, 6, 7)));
    }

    @Test
    void aWriteOfAnUnchangedValueReportsNoChange() {
        Cell cell = Cell.blank(KEY);

        assertTrue(cell.set(1, 2, 3, STONE));
        assertFalse(cell.set(1, 2, 3, STONE));
        assertFalse(cell.set(0, 0, 0, VoxelEntry.AIR));
    }

    @Test
    void thePaletteHoldsOneEntryPerDistinctValue() {
        Cell cell = Cell.blank(KEY);

        cell.set(1, 1, 1, STONE);
        cell.set(2, 2, 2, STONE);
        cell.set(3, 3, 3, GRASS);

        assertEquals(3, cell.paletteSize());
        assertEquals(STONE, cell.get(2, 2, 2));
        assertEquals(GRASS, cell.get(3, 3, 3));
    }

    @Test
    void theMaskFollowsTheOctantOfEveryWrite() {
        Cell cell = Cell.blank(KEY);

        cell.set(20, 4, 20, STONE);

        assertFalse(cell.isEmpty());
        assertEquals(1 << OccupancyMask.octantOf(20, 4, 20), cell.occupancy());

        cell.set(20, 4, 20, VoxelEntry.AIR);

        assertTrue(cell.isEmpty());
    }

    @Test
    void anOctantStaysOccupiedUntilItsLastVoxelIsCleared() {
        Cell cell = Cell.blank(KEY);

        cell.set(1, 1, 1, STONE);
        cell.set(2, 2, 2, GRASS);
        cell.set(1, 1, 1, VoxelEntry.AIR);

        assertFalse(cell.isEmpty());

        cell.set(2, 2, 2, VoxelEntry.AIR);

        assertTrue(cell.isEmpty());
    }

    @Test
    void expandAndReadBackCarryEveryVoxel() {
        Cell cell = Cell.blank(KEY);
        long[] scratch = new long[DetailLevel.VOXELS_PER_CELL];
        cell.expand(scratch);

        assertFalse(cell.readBack(scratch));

        for (int y = 0; y < DetailLevel.VOXELS_PER_SIDE / 2; y++) {
            for (int z = 0; z < DetailLevel.VOXELS_PER_SIDE; z++) {
                for (int x = 0; x < DetailLevel.VOXELS_PER_SIDE; x++) {
                    scratch[DetailLevel.voxelIndex(x, y, z)] = STONE;
                }
            }
        }

        assertTrue(cell.readBack(scratch));
        assertEquals(STONE, cell.get(0, 15, 0));
        assertEquals(VoxelEntry.AIR, cell.get(0, 16, 0));
        assertEquals(2, cell.paletteSize());
        assertEquals(0b1111, cell.occupancy());
    }

    @Test
    void aRestoredCellRecomputesItsMask() {
        long[] palette = {VoxelEntry.AIR, STONE};
        short[] indices = new short[DetailLevel.VOXELS_PER_CELL];
        indices[DetailLevel.voxelIndex(20, 20, 20)] = 1;

        Cell cell = Cell.of(KEY, palette, indices);

        assertEquals(STONE, cell.get(20, 20, 20));
        assertEquals(VoxelEntry.AIR, cell.get(0, 0, 0));
        assertEquals(1 << OccupancyMask.octantOf(20, 20, 20), cell.occupancy());
    }

    @Test
    void thePaletteSurvivesMoreDistinctWritesThanTheIndexWidth() {
        Cell cell = Cell.blank(KEY);
        int writes = 70_000;

        for (int state = 1; state <= writes; state++) {
            assertTrue(cell.set(0, 0, 0, VoxelEntry.pack(state, 0, 0)));
        }

        assertEquals(VoxelEntry.pack(writes, 0, 0), cell.get(0, 0, 0));
        assertTrue(cell.paletteSize() <= DetailLevel.VOXELS_PER_CELL);
        assertEquals(1, cell.occupancy());
    }
}
