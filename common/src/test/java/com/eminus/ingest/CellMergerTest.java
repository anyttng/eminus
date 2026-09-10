package com.eminus.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.eminus.cell.CellFrame;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellAccess;
import com.eminus.cell.cache.CellCache;
import com.eminus.cell.cache.CellHandle;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkerHarness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class CellMergerTest {
    private static final int WORKER_THREADS = 1;
    private static final int LOWEST_STORED_LEVEL = 0;
    private static final int MIN_BLOCK_Y = -64;
    private static final int STONE = 1;
    private static final int INTERIOR = 5;

    private static final int[] OPACITY = {0, 15};
    private static final StateOpacity OPACITIES = state -> OPACITY[state];

    private final AtomicLong clock = new AtomicLong();
    private final FakeCellStore store = new FakeCellStore();
    private final List<CellHandle> saved = Collections.synchronizedList(new ArrayList<>());
    private final CountingCells cells = new CountingCells(new CellCache(store, saved::add, clock::get));
    private final List<Change> changes = Collections.synchronizedList(new ArrayList<>());
    private final CellMerger merger =
            new CellMerger(cells, new CellFrame(MIN_BLOCK_Y), LOWEST_STORED_LEVEL, this::record);
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final SectionPyramid pyramid = new SectionPyramid();

    @AfterEach
    void stopTheHarness() {
        harness.close();
    }

    @Test
    void aFirstMergeReachesEveryLevelFromTheLowestStoredOne() {
        buildFrom(entry(STONE));

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertEquals(DetailLevel.COUNT, cells.opens());
        assertEquals(DetailLevel.COUNT, changes.size());
    }

    @Test
    void anUnchangedLevelStopsTheClimb() {
        buildFrom(entry(STONE));
        harness.run(() -> merger.merge(pyramid, 0, 0, 0));
        int opened = cells.opens();
        changes.clear();

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertEquals(1, cells.opens() - opened);
        assertTrue(changes.isEmpty());
    }

    @Test
    void aChangeOnTheCellBoundarySetsThatFacesBit() {
        buildFrom(VoxelEntry.AIR);
        set(0, 0, 0, entry(STONE));
        PyramidDownsampler.build(pyramid, OPACITIES);

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertEquals(FaceMask.DOWN | FaceMask.NORTH | FaceMask.WEST, changes.get(0).faceMask());
    }

    @Test
    void anInteriorChangeSetsNoFace() {
        buildFrom(VoxelEntry.AIR);
        set(INTERIOR, INTERIOR, INTERIOR, entry(STONE));
        PyramidDownsampler.build(pyramid, OPACITIES);

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertEquals(FaceMask.NONE, changes.get(0).faceMask());
    }

    @Test
    void theChangedCellArrivesDirtyAndNoLongerEmpty() {
        buildFrom(VoxelEntry.AIR);
        set(INTERIOR, INTERIOR, INTERIOR, entry(STONE));
        PyramidDownsampler.build(pyramid, OPACITIES);

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        Change change = changes.get(0);
        assertTrue(change.dirty());
        assertFalse(change.empty());
    }

    private void record(CellHandle handle, int faceMask) {
        changes.add(new Change(faceMask, handle.dirty(), handle.cell().isEmpty()));
        cells.release(handle);
    }

    private void buildFrom(long entry) {
        Arrays.fill(pyramid.level(DetailLevel.MIN), entry);
        PyramidDownsampler.build(pyramid, OPACITIES);
    }

    private void set(int x, int y, int z, long entry) {
        pyramid.level(DetailLevel.MIN)[SectionPyramid.indexAt(SectionPyramid.SECTION_SIDE, x, y, z)] = entry;
    }

    private static long entry(int state) {
        return VoxelEntry.pack(state, 0, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0));
    }

    private record Change(int faceMask, boolean dirty, boolean empty) {
    }

    private static final class CountingCells implements CellAccess {
        private final CellCache cache;
        private final AtomicInteger opens = new AtomicInteger();

        private CountingCells(CellCache cache) {
            this.cache = cache;
        }

        @Override
        public CellHandle open(long key) {
            opens.incrementAndGet();
            return cache.open(key);
        }

        @Override
        public void release(CellHandle handle) {
            cache.release(handle);
        }

        private int opens() {
            return opens.get();
        }
    }
}
