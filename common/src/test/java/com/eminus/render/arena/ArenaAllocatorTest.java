package com.eminus.render.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArenaAllocatorTest {
    private static final int ARENA_BLOCKS = 8;
    private static final int ONE_BLOCK = ArenaAllocator.QUADS_PER_BLOCK;
    private static final int TWO_BLOCKS = 2 * ArenaAllocator.QUADS_PER_BLOCK;
    private static final int THREE_BLOCKS = 3 * ArenaAllocator.QUADS_PER_BLOCK;

    private final ArenaAllocator allocator = new ArenaAllocator(ARENA_BLOCKS);

    @Test
    void aSingleQuadTakesAWholeBlock() {
        assertEquals(0, allocator.allocate(1));
        assertEquals(1, allocator.usedBlocks());
    }

    @Test
    void aGrownArenaMergesItsNewBlocksWithTheFreeTail() {
        allocator.allocate(THREE_BLOCKS);
        allocator.allocate(THREE_BLOCKS);

        allocator.grow(ARENA_BLOCKS + 4);

        assertEquals(1, allocator.freeRuns());
        assertEquals(6, allocator.largestFreeRun());
        assertEquals(6, allocator.allocate(6 * ONE_BLOCK));
    }

    @Test
    void aFullArenaGrownKeepsItsBlocksAndPlacesPastThem() {
        for (int index = 0; index < ARENA_BLOCKS; index++) {
            allocator.allocate(ONE_BLOCK);
        }

        allocator.grow(ARENA_BLOCKS + 2);

        assertEquals(ARENA_BLOCKS, allocator.allocate(TWO_BLOCKS));
        assertEquals(ARENA_BLOCKS + 2, allocator.usedBlocks());
    }

    @Test
    void aFreshArenaPlacesRangesBackToBack() {
        ArenaAllocator packing = new ArenaAllocator(6);

        assertEquals(0, packing.allocate(TWO_BLOCKS));
        assertEquals(2, packing.allocate(ONE_BLOCK));
        assertEquals(3, packing.allocate(THREE_BLOCKS));
        assertEquals(0, packing.freeBlocks());
    }

    @Test
    void anAllocationTakesTheEarliestRangeThatHoldsIt() {
        allocator.allocate(ONE_BLOCK);
        int middle = allocator.allocate(TWO_BLOCKS);
        allocator.allocate(ONE_BLOCK);
        allocator.free(middle, TWO_BLOCKS);

        assertEquals(middle, allocator.allocate(ONE_BLOCK));
    }

    @Test
    void adjacentFreedRangesMergeIntoOne() {
        int first = allocator.allocate(ONE_BLOCK);
        int second = allocator.allocate(TWO_BLOCKS);
        allocator.allocate(ONE_BLOCK);

        allocator.free(second, TWO_BLOCKS);
        allocator.free(first, ONE_BLOCK);

        assertEquals(first, allocator.allocate(THREE_BLOCKS));
    }

    @Test
    void freeingBothNeighborsOfARangeLeavesOneRun() {
        int[] blocks = new int[ARENA_BLOCKS];
        for (int index = 0; index < ARENA_BLOCKS; index++) {
            blocks[index] = allocator.allocate(ONE_BLOCK);
        }

        allocator.free(blocks[0], ONE_BLOCK);
        allocator.free(blocks[2], ONE_BLOCK);
        allocator.free(blocks[1], ONE_BLOCK);

        assertEquals(1, allocator.freeRuns());
        assertEquals(3, allocator.largestFreeRun());
    }

    @Test
    void freeSpaceScatteredAcrossRunsHoldsNothingLargerThanARun() {
        int[] blocks = new int[ARENA_BLOCKS];
        for (int index = 0; index < ARENA_BLOCKS; index++) {
            blocks[index] = allocator.allocate(ONE_BLOCK);
        }

        for (int index = 0; index < ARENA_BLOCKS; index += 2) {
            allocator.free(blocks[index], ONE_BLOCK);
        }

        assertEquals(4, allocator.freeBlocks());
        assertEquals(1, allocator.largestFreeRun());
        assertEquals(ArenaAllocator.NO_BLOCK, allocator.allocate(TWO_BLOCKS));
    }

    @Test
    void aMeshLargerThanTheArenaIsRefusedAndConsumesNothing() {
        assertEquals(ArenaAllocator.NO_BLOCK, allocator.allocate((ARENA_BLOCKS + 1) * ArenaAllocator.QUADS_PER_BLOCK));
        assertEquals(0, allocator.usedBlocks());
        assertEquals(1, allocator.freeRuns());
    }

    @Test
    void anEmptyMeshIsRefusedWithoutTouchingTheArena() {
        assertEquals(ArenaAllocator.NO_BLOCK, allocator.allocate(0));
        assertEquals(ARENA_BLOCKS, allocator.freeBlocks());
    }
}
