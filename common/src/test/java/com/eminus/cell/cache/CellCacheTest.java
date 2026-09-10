package com.eminus.cell.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.VoxelEntry;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkerHarness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class CellCacheTest {
    private static final int WORKER_THREADS = 2;
    private static final long READ_START_MILLIS = 10_000L;
    private static final long KEY = CellKey.pack(0, 1, 2, 3);

    private final AtomicLong clock = new AtomicLong();
    private final FakeCellStore store = new FakeCellStore();
    private final List<CellHandle> submitted = Collections.synchronizedList(new ArrayList<>());
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final CellCache cache = new CellCache(store, submitted::add, clock::get);

    @AfterEach
    void stopTheHarness() {
        harness.close();
    }

    @Test
    void anAbsentCellOpensAsEmptyAir() {
        CellHandle handle = harness.call(() -> cache.open(KEY));

        Cell cell = handle.cell();
        assertTrue(cell.isEmpty());
        assertEquals(VoxelEntry.AIR, cell.get(0, 0, 0));
        assertEquals(1, store.reads());
    }

    @Test
    void twoOpensOfOneKeyLoadOnceAndShareTheInstance() {
        store.holdReads();
        AtomicReference<CellHandle> first = new AtomicReference<>();
        AtomicReference<CellHandle> second = new AtomicReference<>();
        CountDownLatch opened = new CountDownLatch(2);

        harness.service().enqueue(scratch -> {
            first.set(cache.open(KEY));
            opened.countDown();
        });
        assertTrue(store.awaitReadStarted(READ_START_MILLIS));
        harness.service().enqueue(scratch -> {
            second.set(cache.open(KEY));
            opened.countDown();
        });
        store.releaseReads();
        WorkerHarness.await(opened);

        assertSame(first.get(), second.get());
        assertEquals(1, store.reads());
        assertEquals(1, cache.liveCount());
    }

    @Test
    void openingOffAWorkerThreadIsRefused() {
        assertThrows(IllegalStateException.class, () -> cache.open(KEY));
    }

    @Test
    void aCleanReleaseParksTheCellAndAReopenTakesItBack() {
        CellHandle first = harness.call(() -> cache.open(KEY));
        harness.run(() -> cache.release(first));

        assertEquals(0, cache.liveCount());
        assertEquals(1, cache.parkedCount());

        CellHandle second = harness.call(() -> cache.open(KEY));
        assertSame(first, second);
        assertEquals(1, store.reads());
    }

    @Test
    void theParkedSetIsBoundedAndDropsTheOldest() {
        for (int index = 0; index <= ReleasedCells.MAX_PARKED; index++) {
            openAndRelease(CellKey.pack(0, index, 0, 0));
        }

        assertEquals(ReleasedCells.MAX_PARKED, cache.parkedCount());

        int readsBefore = store.reads();
        openAndRelease(CellKey.pack(0, 0, 0, 0));
        assertEquals(readsBefore + 1, store.reads());
    }

    @Test
    void aDirtyReleaseSubmitsTheCellExactlyOnce() {
        CellHandle handle = harness.call(() -> cache.open(KEY));
        handle.markDirty();
        harness.run(() -> cache.release(handle));

        assertEquals(List.of(handle), submitted);
        assertEquals(1, cache.liveCount());
        assertEquals(0, cache.parkedCount());

        cache.sweep();
        assertEquals(1, submitted.size());
    }

    @Test
    void aCellHeldDirtyPastTheCapIsSubmittedWithoutARelease() {
        CellHandle handle = harness.call(() -> cache.open(KEY));
        handle.markDirty();

        cache.sweep();
        assertEquals(List.of(), submitted);

        clock.addAndGet(CellHandle.DIRTY_CAP_MILLIS);
        cache.sweep();
        assertEquals(List.of(handle), submitted);

        clock.addAndGet(CellHandle.DIRTY_CAP_MILLIS);
        cache.sweep();
        assertEquals(1, submitted.size());
    }

    @Test
    void theSweepParksACellSavedAfterItsRelease() {
        CellHandle handle = harness.call(() -> cache.open(KEY));
        handle.markDirty();
        harness.run(() -> cache.release(handle));
        handle.save(store);

        assertEquals(1, store.writes());

        cache.sweep();
        assertEquals(0, cache.liveCount());
        assertEquals(1, cache.parkedCount());
    }

    private void openAndRelease(long key) {
        harness.run(() -> cache.release(cache.open(key)));
    }
}
