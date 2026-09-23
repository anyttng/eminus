package com.eminus.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.eminus.cell.Cell;
import com.eminus.cell.CellFrame;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellCache;
import com.eminus.cell.cache.CellHandle;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkerHarness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class ConcurrentMergeTest {
    private static final int SECTIONS_PER_SIDE = 2;
    private static final int SECTIONS = SECTIONS_PER_SIDE * SECTIONS_PER_SIDE * SECTIONS_PER_SIDE;
    private static final int SIDE = SectionPyramid.SECTION_SIDE;
    private static final int VOXELS_PER_SECTION = SIDE * SIDE * SIDE;
    private static final int LOWEST_STORED_LEVEL = 0;
    private static final int MIN_BLOCK_Y = -64;
    private static final int FULL_OPACITY = 15;
    private static final int FIRST_STATE_ID = 1;

    private static final StateOpacity OPACITIES = state -> FULL_OPACITY;

    private final AtomicLong clock = new AtomicLong();
    private final FakeCellStore store = new FakeCellStore();
    private final List<CellHandle> saved = Collections.synchronizedList(new ArrayList<>());
    private final CellCache cells = new CellCache(store, saved::add, clock::get);
    private final CellFrame frame = new CellFrame(MIN_BLOCK_Y);
    private final CellMerger merger = new CellMerger(cells, frame, LOWEST_STORED_LEVEL,
            (handle, faceMask, edgeMask) -> cells.release(handle));
    private final WorkerHarness harness = new WorkerHarness(SECTIONS);

    @AfterEach
    void stopTheHarness() {
        harness.close();
    }

    @Test
    void sectionsSharingOneCellMergeWithoutTearingItsPalette() {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(SECTIONS);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int section = 0; section < SECTIONS; section++) {
            int index = section;
            harness.service().enqueue(scratch -> mergeSection(index, start, done, failure));
        }

        start.countDown();
        WorkerHarness.await(done);

        assertNull(failure.get());
        assertEquals(0, mismatches());
    }

    private void mergeSection(int index, CountDownLatch start, CountDownLatch done,
            AtomicReference<Throwable> failure) {
        try {
            SectionPyramid pyramid = new SectionPyramid();
            fill(pyramid, index);
            PyramidDownsampler.build(pyramid, OPACITIES);
            WorkerHarness.await(start);
            merger.merge(pyramid, sectionX(index), sectionY(index), sectionZ(index));
        } catch (Throwable thrown) {
            failure.compareAndSet(null, thrown);
        } finally {
            done.countDown();
        }
    }

    private int mismatches() {
        CellHandle handle = harness.call(() -> cells.open(frame.keyAt(LOWEST_STORED_LEVEL, 0, 0, 0)));

        try {
            return handle.withCell(this::countMismatches);
        } finally {
            harness.run(() -> cells.release(handle));
        }
    }

    private int countMismatches(Cell cell) {
        int mismatches = 0;

        for (int index = 0; index < SECTIONS; index++) {
            int originX = frame.voxelX(sectionX(index) * SIDE, LOWEST_STORED_LEVEL);
            int originY = frame.voxelY(sectionY(index) * SIDE, LOWEST_STORED_LEVEL);
            int originZ = frame.voxelZ(sectionZ(index) * SIDE, LOWEST_STORED_LEVEL);

            for (int y = 0; y < SIDE; y++) {
                for (int z = 0; z < SIDE; z++) {
                    for (int x = 0; x < SIDE; x++) {
                        if (cell.get(originX + x, originY + y, originZ + z) != entryOf(index, x, y, z)) {
                            mismatches++;
                        }
                    }
                }
            }
        }

        return mismatches;
    }

    private static void fill(SectionPyramid pyramid, int index) {
        pyramid.reset();
        long[] level = pyramid.level(LOWEST_STORED_LEVEL);

        for (int y = 0; y < SIDE; y++) {
            for (int z = 0; z < SIDE; z++) {
                for (int x = 0; x < SIDE; x++) {
                    level[SectionPyramid.indexAt(SIDE, x, y, z)] = entryOf(index, x, y, z);
                }
            }
        }
    }

    private static long entryOf(int index, int x, int y, int z) {
        int stateId = FIRST_STATE_ID + index * VOXELS_PER_SECTION + SectionPyramid.indexAt(SIDE, x, y, z);
        return VoxelEntry.pack(stateId, 0, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0));
    }

    private static int sectionX(int index) {
        return index % SECTIONS_PER_SIDE;
    }

    private static int sectionY(int index) {
        return (index / SECTIONS_PER_SIDE) % SECTIONS_PER_SIDE;
    }

    private static int sectionZ(int index) {
        return index / (SECTIONS_PER_SIDE * SECTIONS_PER_SIDE);
    }
}
