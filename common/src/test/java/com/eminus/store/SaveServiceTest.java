package com.eminus.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import com.eminus.cell.CellKey;
import com.eminus.cell.cache.CellCache;
import com.eminus.cell.cache.CellHandle;
import com.eminus.work.WorkService;
import com.eminus.work.WorkerHarness;
import com.eminus.work.WorkerPool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class SaveServiceTest {
    private static final int WORKER_THREADS = 1;
    private static final long KEY = CellKey.pack(0, 4, 5, 6);
    private static final String PARKED_SERVICE = "save";

    private final AtomicLong clock = new AtomicLong();
    private final FakeCellStore store = new FakeCellStore();
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final SaveService saves = new SaveService(store, harness.service());
    private final CellCache cache = new CellCache(store, saves, clock::get);
    private final WorkerPool idlePool = WorkerPool.start(0);

    @AfterEach
    void stopThePools() {
        harness.close();
        idlePool.shutdown();
    }

    @Test
    void theQueuedCellIsWrittenByTheWorkerPool() {
        CellHandle handle = openDirty(cache);
        harness.run(() -> cache.release(handle));
        harness.close();

        assertEquals(1, store.writes());
        assertFalse(handle.dirty());
        assertEquals(0, saves.pending());
    }

    @Test
    void aCellIsQueuedOnceHoweverOftenItIsSubmitted() {
        SaveService parked = parkedSaves();
        CellCache parkedCache = new CellCache(store, parked, clock::get);
        releaseDirty(parkedCache, KEY);
        clock.addAndGet(CellHandle.DIRTY_CAP_MILLIS);
        parkedCache.sweep();

        assertEquals(1, parked.pending());
    }

    @Test
    void aFailedWriteKeepsTheCellDirtyAndComesBackAfterTheBackoff() {
        SaveService parked = parkedSaves();
        CellCache parkedCache = new CellCache(store, parked, clock::get);
        store.refuseWrites(true);
        CellHandle handle = openDirty(parkedCache);
        harness.run(() -> parkedCache.release(handle));
        parked.flush();

        assertTrue(handle.dirty());
        assertEquals(0, store.writes());

        parkedCache.sweep();
        assertEquals(0, parked.pending());

        clock.addAndGet(CellHandle.RETRY_MILLIS);
        store.refuseWrites(false);
        parkedCache.sweep();
        parked.flush();

        assertFalse(handle.dirty());
        assertEquals(1, store.writes());
    }

    @Test
    void anEnqueueOverTheSoftCapWritesInline() {
        SaveService capped = parkedSaves();
        CellCache cappedCache = new CellCache(store, capped, clock::get);

        for (int index = 0; index <= SaveService.SOFT_CAP; index++) {
            releaseDirty(cappedCache, CellKey.pack(0, index, 0, 0));
        }

        assertEquals(SaveService.SOFT_CAP, capped.pending());
        assertEquals(1, store.writes());
    }

    private CellHandle openDirty(CellCache target) {
        return harness.call(() -> {
            CellHandle handle = target.open(KEY);
            handle.markDirty();
            return handle;
        });
    }

    private SaveService parkedSaves() {
        return new SaveService(store, idlePool.register(PARKED_SERVICE, 1, WorkService.UNLIMITED, () -> null));
    }

    private void releaseDirty(CellCache target, long key) {
        harness.run(() -> {
            CellHandle handle = target.open(key);
            handle.markDirty();
            target.release(handle);
        });
    }
}
