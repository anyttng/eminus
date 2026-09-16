package com.eminus.render.arena;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class ArenaChurnTest {
    private static final int ARENA_BLOCKS = 100_000;
    private static final int HIGH_WATER_PERCENT = 85;
    private static final int WHOLE_PERCENT = 100;
    private static final int MIN_QUADS = 2_000;
    private static final int MAX_QUADS = 23_000;
    private static final int EVICTIONS_PER_CYCLE = 64;
    private static final int BUILDS_PER_CYCLE = 32;
    private static final int CYCLES = 2_000;
    private static final long SEED = 20260912L;

    private record Placed(int block, int quads) {
    }

    private final ArenaAllocator allocator = new ArenaAllocator(ARENA_BLOCKS);
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

        assertTrue(tightest >= ArenaAllocator.blocksFor(MAX_QUADS),
                "The largest free run fell to " + tightest + " blocks against a largest request of "
                        + ArenaAllocator.blocksFor(MAX_QUADS) + "; the mark left "
                        + allocator.freeBlocks() + " blocks free across " + allocator.freeRuns()
                        + " runs holding " + live.size() + " meshes, and " + refusals + " were refused");
    }

    private boolean overTheHighWaterMark() {
        return (long) allocator.usedBlocks() * WHOLE_PERCENT >= (long) ARENA_BLOCKS * HIGH_WATER_PERCENT;
    }

    private void fillToTheHighWaterMark() {
        while (!overTheHighWaterMark()) {
            place(nextQuads(), 0);
        }
    }

    private void place(int quads, int cycle) {
        int wanted = ArenaAllocator.blocksFor(quads);
        int free = allocator.freeBlocks();
        int block = allocator.allocate(quads);
        tightest = Math.min(tightest, allocator.largestFreeRun());

        if (block == ArenaAllocator.NO_BLOCK) {
            refusals++;
            assertTrue(free < wanted, "Cycle " + cycle + ": a mesh of " + quads + " quads wanting " + wanted
                    + " blocks was refused while " + free + " blocks were free, the largest run "
                    + allocator.largestFreeRun() + " blocks across " + allocator.freeRuns() + " runs");
            return;
        }

        live.add(new Placed(block, quads));
    }

    private void evictScattered(int count) {
        for (int evicted = 0; evicted < count && !live.isEmpty(); evicted++) {
            int at = churn.nextInt(live.size());
            Placed gone = live.set(at, live.get(live.size() - 1));
            live.remove(live.size() - 1);
            allocator.free(gone.block(), gone.quads());
        }
    }

    private int nextQuads() {
        return MIN_QUADS + churn.nextInt(MAX_QUADS - MIN_QUADS);
    }
}
