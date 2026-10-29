package com.eminus.render.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArenaAllocatorTest {
    private static final int ARENA_SEGMENTS = 8;
    private static final int ONE_SEGMENT = ArenaAllocator.QUADS_PER_SEGMENT;
    private static final int TWO_SEGMENTS = 2 * ArenaAllocator.QUADS_PER_SEGMENT;
    private static final int THREE_SEGMENTS = 3 * ArenaAllocator.QUADS_PER_SEGMENT;

    private final ArenaAllocator allocator = new ArenaAllocator(ARENA_SEGMENTS);

    @Test
    void aSingleQuadTakesAWholeSegment() {
        assertEquals(0, allocator.allocate(1));
        assertEquals(1, allocator.usedSegments());
    }

    @Test
    void aGrownArenaMergesItsNewSegmentsWithTheFreeTail() {
        allocator.allocate(THREE_SEGMENTS);
        allocator.allocate(THREE_SEGMENTS);

        allocator.grow(ARENA_SEGMENTS + 4);

        assertEquals(1, allocator.freeRuns());
        assertEquals(6, allocator.largestFreeRun());
        assertEquals(6, allocator.allocate(6 * ONE_SEGMENT));
    }

    @Test
    void aFullArenaGrownKeepsItsSegmentsAndPlacesPastThem() {
        for (int index = 0; index < ARENA_SEGMENTS; index++) {
            allocator.allocate(ONE_SEGMENT);
        }

        allocator.grow(ARENA_SEGMENTS + 2);

        assertEquals(ARENA_SEGMENTS, allocator.allocate(TWO_SEGMENTS));
        assertEquals(ARENA_SEGMENTS + 2, allocator.usedSegments());
    }

    @Test
    void aFreshArenaPlacesRangesBackToBack() {
        ArenaAllocator packing = new ArenaAllocator(6);

        assertEquals(0, packing.allocate(TWO_SEGMENTS));
        assertEquals(2, packing.allocate(ONE_SEGMENT));
        assertEquals(3, packing.allocate(THREE_SEGMENTS));
        assertEquals(0, packing.freeSegments());
    }

    @Test
    void anAllocationTakesTheEarliestRangeThatHoldsIt() {
        allocator.allocate(ONE_SEGMENT);
        int middle = allocator.allocate(TWO_SEGMENTS);
        allocator.allocate(ONE_SEGMENT);
        allocator.free(middle, TWO_SEGMENTS);

        assertEquals(middle, allocator.allocate(ONE_SEGMENT));
    }

    @Test
    void adjacentFreedRangesMergeIntoOne() {
        int first = allocator.allocate(ONE_SEGMENT);
        int second = allocator.allocate(TWO_SEGMENTS);
        allocator.allocate(ONE_SEGMENT);

        allocator.free(second, TWO_SEGMENTS);
        allocator.free(first, ONE_SEGMENT);

        assertEquals(first, allocator.allocate(THREE_SEGMENTS));
    }

    @Test
    void freeingBothNeighborsOfARangeLeavesOneRun() {
        int[] segments = new int[ARENA_SEGMENTS];
        for (int index = 0; index < ARENA_SEGMENTS; index++) {
            segments[index] = allocator.allocate(ONE_SEGMENT);
        }

        allocator.free(segments[0], ONE_SEGMENT);
        allocator.free(segments[2], ONE_SEGMENT);
        allocator.free(segments[1], ONE_SEGMENT);

        assertEquals(1, allocator.freeRuns());
        assertEquals(3, allocator.largestFreeRun());
    }

    @Test
    void freeSpaceScatteredAcrossRunsHoldsNothingLargerThanARun() {
        int[] segments = new int[ARENA_SEGMENTS];
        for (int index = 0; index < ARENA_SEGMENTS; index++) {
            segments[index] = allocator.allocate(ONE_SEGMENT);
        }

        for (int index = 0; index < ARENA_SEGMENTS; index += 2) {
            allocator.free(segments[index], ONE_SEGMENT);
        }

        assertEquals(4, allocator.freeSegments());
        assertEquals(1, allocator.largestFreeRun());
        assertEquals(ArenaAllocator.NO_SEGMENT, allocator.allocate(TWO_SEGMENTS));
    }

    @Test
    void aMeshLargerThanTheArenaIsRefusedAndConsumesNothing() {
        assertEquals(ArenaAllocator.NO_SEGMENT,
                allocator.allocate((ARENA_SEGMENTS + 1) * ArenaAllocator.QUADS_PER_SEGMENT));
        assertEquals(0, allocator.usedSegments());
        assertEquals(1, allocator.freeRuns());
    }

    @Test
    void anEmptyMeshIsRefusedWithoutTouchingTheArena() {
        assertEquals(ArenaAllocator.NO_SEGMENT, allocator.allocate(0));
        assertEquals(ARENA_SEGMENTS, allocator.freeSegments());
    }
}
