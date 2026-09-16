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
        CellHandle handle = openDirty();
        harness.run(() -> cache.release(handle));
        harness.close();

        assertEquals(1, store.writes());
        assertFalse(handle.dirty());
        assertEquals(0, saves.pending());
    }

    @Test
    void aCellIsQueuedOnceHoweverOftenItIsSubmitted() {
        CellHandle handle = openDirty();
        harness.run(() -> cache.release(handle));
        cache.sweep();
        saves.flush();

        assertEquals(1, store.writes());
    }

    @Test
    void aFailedWriteKeepsTheCellDirtyAndComesBackAfterTheBackoff() {
        store.refuseWrites(true);
        CellHandle handle = openDirty();
        harness.run(() -> cache.release(handle));
        saves.flush();

        assertTrue(handle.dirty());
        assertEquals(0, store.writes());

        cache.sweep();
        assertEquals(0, saves.pending());

        clock.addAndGet(CellHandle.RETRY_MILLIS);
        store.refuseWrites(false);
        cache.sweep();
        saves.flush();

        assertFalse(handle.dirty());
        assertEquals(1, store.writes());
    }

    @Test
    void anEnqueueOverTheSoftCapWritesInline() {
        WorkService<Void> parked = idlePool.register("save", 1, WorkService.UNLIMITED, () -> null);
        SaveService capped = new SaveService(store, parked);
        CellCache cappedCache = new CellCache(store, capped, clock::get);

        for (int index = 0; index <= SaveService.SOFT_CAP; index++) {
            long key = CellKey.pack(0, index, 0, 0);
            harness.run(() -> {
                CellHandle handle = cappedCache.open(key);
                handle.markDirty();
                cappedCache.release(handle);
            });
        }

        assertEquals(SaveService.SOFT_CAP, capped.pending());
        assertEquals(1, store.writes());
    }

    private CellHandle openDirty() {
        return harness.call(() -> {
            CellHandle handle = cache.open(KEY);
            handle.markDirty();
            return handle;
        });
    }
}
