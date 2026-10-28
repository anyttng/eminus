package com.eminus.render.arena;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class ArenaChurnTest {
    private static final int ARENA_SEGMENTS = 100_000;
    private static final int HIGH_WATER_PERCENT = 85;
    private static final int WHOLE_PERCENT = 100;
    private static final int MIN_QUADS = 2_000;
    private static final int MAX_QUADS = 23_000;
    private static final int EVICTIONS_PER_CYCLE = 64;
    private static final int BUILDS_PER_CYCLE = 32;
    private static final int CYCLES = 2_000;
    private static final long SEED = 20260912L;

    private record Placed(int segment, int quads) {
    }

    private final ArenaAllocator allocator = new ArenaAllocator(ARENA_SEGMENTS);
    private final List<Placed> live = new ArrayList<>();
    private final Random churn = new Random(SEED);

    private int refusals;
    private int tightest = Integer.MAX_VALUE;

    @Test
    void underSteadyChurnNoMeshIsRefusedWhileTheFreeSpaceCouldHoldIt() {
        fillToTheHighWaterMark();

        for (int cycle = 0; cycle < CYCLES; cycle++) {
            if (overTheHighWaterMark()) {
                evictScattered(EVICTIONS_PER_CYCLE);
            }

            for (int build = 0; build < BUILDS_PER_CYCLE; build++) {
                place(nextQuads(), cycle);
            }
        }

        assertTrue(tightest >= ArenaAllocator.segmentsFor(MAX_QUADS),
                "The largest free run fell to " + tightest + " segments against a largest request of "
                        + ArenaAllocator.segmentsFor(MAX_QUADS) + "; the mark left "
                        + allocator.freeSegments() + " segments free across " + allocator.freeRuns()
                        + " runs holding " + live.size() + " meshes, and " + refusals + " were refused");
    }

    private boolean overTheHighWaterMark() {
        return (long) allocator.usedSegments() * WHOLE_PERCENT >= (long) ARENA_SEGMENTS * HIGH_WATER_PERCENT;
    }

    private void fillToTheHighWaterMark() {
        while (!overTheHighWaterMark()) {
            place(nextQuads(), 0);
        }
    }

    private void place(int quads, int cycle) {
        int wanted = ArenaAllocator.segmentsFor(quads);
        int free = allocator.freeSegments();
        int segment = allocator.allocate(quads);
        tightest = Math.min(tightest, allocator.largestFreeRun());

        if (segment == ArenaAllocator.NO_SEGMENT) {
            refusals++;
            assertTrue(free < wanted, "Cycle " + cycle + ": a mesh of " + quads + " quads wanting " + wanted
                    + " segments was refused while " + free + " segments were free, the largest run "
                    + allocator.largestFreeRun() + " segments across " + allocator.freeRuns() + " runs");
            return;
        }

        live.add(new Placed(segment, quads));
    }

    private void evictScattered(int count) {
        for (int evicted = 0; evicted < count && !live.isEmpty(); evicted++) {
            int at = churn.nextInt(live.size());
            Placed gone = live.set(at, live.get(live.size() - 1));
            live.remove(live.size() - 1);
            allocator.free(gone.segment(), gone.quads());
        }
    }

    private int nextQuads() {
        return MIN_QUADS + churn.nextInt(MAX_QUADS - MIN_QUADS);
    }
}
