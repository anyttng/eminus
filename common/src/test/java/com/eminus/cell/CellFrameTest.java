package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CellFrameTest {
    private static final int[] MIN_BLOCK_YS = {-64, -16, 0};
    private static final int[] HORIZONTALS = {-1_000_000, -513, -33, -1, 0, 1, 33, 513, 1_000_000};
    private static final int[] HEIGHTS = {0, 1, 63, 200, 319};

    @Test
    void everyLevelCarriesABlockPositionThroughTheKeyAndBack() {
        for (int minBlockY : MIN_BLOCK_YS) {
            CellFrame frame = new CellFrame(minBlockY);

            for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
                int voxelSize = DetailLevel.blocksPerVoxel(level);

                for (int blockX : HORIZONTALS) {
                    for (int blockZ : HORIZONTALS) {
                        for (int height : HEIGHTS) {
                            int blockY = minBlockY + height;
                            long key = frame.keyAt(level, blockX, blockY, blockZ);

                            assertEquals(level, CellKey.level(key));
                            assertEquals(Math.floorDiv(blockX, voxelSize) * voxelSize,
                                    frame.blockXOf(key, frame.voxelX(blockX, level)));
                            assertEquals(Math.floorDiv(blockZ, voxelSize) * voxelSize,
                                    frame.blockZOf(key, frame.voxelZ(blockZ, level)));
                            assertEquals(minBlockY + Math.floorDiv(height, voxelSize) * voxelSize,
                                    frame.blockYOf(key, frame.voxelY(blockY, level)));
                        }
                    }
                }
            }
        }
    }

    @Test
    void everyVoxelCoordinateStaysInsideTheCell() {
        for (int minBlockY : MIN_BLOCK_YS) {
            CellFrame frame = new CellFrame(minBlockY);

            for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
                for (int blockX : HORIZONTALS) {
                    for (int height : HEIGHTS) {
                        assertInsideTheCell(frame.voxelX(blockX, level));
                        assertInsideTheCell(frame.voxelZ(blockX, level));
                        assertInsideTheCell(frame.voxelY(minBlockY + height, level));
                    }
                }
            }
        }
    }

    @Test
    void theDimensionsLowestBlockIsTheFirstVoxelOfTheFirstCell() {
        for (int minBlockY : MIN_BLOCK_YS) {
            CellFrame frame = new CellFrame(minBlockY);

            for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
                assertEquals(0, frame.cellY(minBlockY, level));
                assertEquals(0, frame.voxelY(minBlockY, level));
                assertEquals(minBlockY, frame.originBlockY(0, level));
                assertEquals(-1, frame.cellY(minBlockY - 1, level));
            }
        }
    }

    @Test
    void aTopLevelCellSpansFiveHundredAndTwelveBlocks() {
        CellFrame frame = new CellFrame(-64);

        assertEquals(0, frame.cellX(0, DetailLevel.MAX));
        assertEquals(0, frame.cellX(511, DetailLevel.MAX));
        assertEquals(1, frame.cellX(512, DetailLevel.MAX));
        assertEquals(-1, frame.cellX(-1, DetailLevel.MAX));
        assertEquals(-512, frame.originBlockX(-1, DetailLevel.MAX));
    }

    private static void assertInsideTheCell(int voxel) {
        assertTrue(voxel >= 0 && voxel < DetailLevel.VOXELS_PER_SIDE, "voxel coordinate " + voxel);
    }
}
