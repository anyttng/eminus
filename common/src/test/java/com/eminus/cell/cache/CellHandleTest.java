package com.eminus.cell.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import com.eminus.cell.CellKey;
import com.eminus.cell.VoxelEntry;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkerHarness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class CellHandleTest {
    private static final int WORKER_THREADS = 3;
    private static final long WRITE_START_MILLIS = 10_000L;
    private static final long SAVING = CellKey.pack(0, 1, 2, 3);
    private static final long OTHER = CellKey.pack(0, 5, 2, 3);
    private static final long STONE = VoxelEntry.pack(17, 4, VoxelEntry.light(0, 0));
    private static final int FIRST_VOXEL = 4;
    private static final int LATER_VOXEL = 20;
    private static final int WRITES_OF_BOTH_SAVES = 2;

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
    void aBlockedWriteStallsNeitherAMergeIntoItsCellNorAnotherCell() {
        CellHandle handle = openChanged();
        CountDownLatch saved = saveBehindAHeldWrite(handle);

        try {
            harness.run(() -> change(handle, LATER_VOXEL));
            harness.run(() -> cache.release(handle));
            harness.run(() -> cache.release(cache.open(OTHER)));
        } finally {
            store.releaseWrites();
        }

        WorkerHarness.await(saved);
    }

    @Test
    void aChangeMadeWhileTheWriteRunsLeavesTheCellDirtyForTheNextSave() {
        CellHandle handle = openChanged();
        CountDownLatch saved = saveBehindAHeldWrite(handle);

        try {
            harness.run(() -> change(handle, LATER_VOXEL));
        } finally {
            store.releaseWrites();
        }

        WorkerHarness.await(saved);

        assertTrue(handle.dirty());
        assertEquals(STONE, store.read(SAVING).get(FIRST_VOXEL, FIRST_VOXEL, FIRST_VOXEL));
        assertEquals(VoxelEntry.AIR, store.read(SAVING).get(LATER_VOXEL, LATER_VOXEL, LATER_VOXEL));

        handle.save(store);

        assertFalse(handle.dirty());
        assertEquals(STONE, store.read(SAVING).get(LATER_VOXEL, LATER_VOXEL, LATER_VOXEL));
        assertEquals(WRITES_OF_BOTH_SAVES, store.writes());
    }

    private CellHandle openChanged() {
        return harness.call(() -> {
            CellHandle handle = cache.open(SAVING);
            change(handle, FIRST_VOXEL);
            return handle;
        });
    }

    private CountDownLatch saveBehindAHeldWrite(CellHandle handle) {
        store.holdWrites();
        CountDownLatch saved = new CountDownLatch(1);
        harness.service().enqueue(scratch -> {
            handle.save(store);
            saved.countDown();
        });
        assertTrue(store.awaitWriteStarted(WRITE_START_MILLIS));
        return saved;
    }

    private static void change(CellHandle handle, int voxel) {
        handle.withCell(cell -> cell.set(voxel, voxel, voxel, STONE));
        handle.markDirty();
    }
}
