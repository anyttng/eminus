package com.eminus.cell.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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
import com.eminus.store.SqliteCellStore;
import com.eminus.store.StoreException;
import com.eminus.work.WorkerHarness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

@Timeout(30)
class CellCacheTest {
    private static final int WORKER_THREADS = 2;
    private static final long READ_START_MILLIS = 10_000L;
    private static final long KEY = CellKey.pack(0, 1, 2, 3);
    private static final long STONE = VoxelEntry.pack(17, 4, VoxelEntry.light(0, 0));
    private static final int KEPT_VOXEL = 4;
    private static final int MERGED_VOXEL = 20;
    private static final int LOWEST_LEVEL = 0;
    private static final long NO_GRACE_MILLIS = 0L;

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

        assertTrue(handle.withCell(Cell::isEmpty));
        long entry = handle.withCell(cell -> cell.get(0, 0, 0));
        assertEquals(VoxelEntry.AIR, entry);
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

    @Test
    void aReadTheStoreRefusesLeavesTheStoredRecordWhole() {
        Cell stored = Cell.blank(KEY);
        stored.set(KEPT_VOXEL, KEPT_VOXEL, KEPT_VOXEL, STONE);
        store.write(stored);
        store.failReads(new StoreException("The fake store refuses reads.", null));

        try {
            CellHandle handle = harness.call(() -> cache.open(KEY));
            handle.withCell(cell -> cell.set(MERGED_VOXEL, MERGED_VOXEL, MERGED_VOXEL, STONE));
            handle.markDirty();
        } catch (StoreException refused) {
            assertEquals(0, cache.liveCount());
        }

        store.failReads(null);
        cache.flush();

        assertEquals(STONE, store.read(KEY).get(KEPT_VOXEL, KEPT_VOXEL, KEPT_VOXEL));
    }

    @Test
    void aReadThatThrowsAnErrorFailsEveryWaiterAndHoldsNoReference() {
        store.holdReads();
        store.failReads(new Error("The fake store breaks reads."));
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Thread> waiter = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(2);

        harness.service().enqueue(scratch -> openCatching(failures, done));
        assertTrue(store.awaitReadStarted(READ_START_MILLIS));
        harness.service().enqueue(scratch -> {
            waiter.set(Thread.currentThread());
            openCatching(failures, done);
        });
        while (waiter.get() == null || waiter.get().getState() != Thread.State.WAITING) {
            Thread.onSpinWait();
        }

        store.releaseReads();
        try {
            WorkerHarness.await(done);
        } finally {
            if (done.getCount() > 0) {
                waiter.get().interrupt();
            }
        }

        assertEquals(2, failures.size());
        assertEquals(0, cache.liveCount());

        store.failReads(null);
        harness.call(() -> cache.open(KEY));
        assertEquals(2, store.reads());
    }

    @Test
    void aDamagedRecordOpensBlankAndIsRewritten(@TempDir Path folder) throws SQLException {
        try (SqliteCellStore sqlite = openSqlite(folder)) {
            sqlite.write(Cell.blank(KEY));
        }

        try (Connection connection = rawConnection(folder); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE cells SET data = x'0102030405060708' WHERE key = " + KEY);
        }

        try (SqliteCellStore sqlite = openSqlite(folder)) {
            CellCache onDisk = new CellCache(sqlite, submitted::add, clock::get);
            CellHandle handle = harness.call(() -> onDisk.open(KEY));
            assertTrue(handle.withCell(Cell::isEmpty));

            handle.withCell(cell -> cell.set(MERGED_VOXEL, MERGED_VOXEL, MERGED_VOXEL, STONE));
            handle.markDirty();
            onDisk.flush();

            assertEquals(STONE, sqlite.read(KEY).get(MERGED_VOXEL, MERGED_VOXEL, MERGED_VOXEL));
        }
    }

    private void openAndRelease(long key) {
        harness.run(() -> cache.release(cache.open(key)));
    }

    private void openCatching(List<Throwable> failures, CountDownLatch done) {
        try {
            cache.open(KEY);
        } catch (Throwable thrown) {
            failures.add(thrown);
        } finally {
            done.countDown();
        }
    }

    private SqliteCellStore openSqlite(Path folder) {
        return SqliteCellStore.open(folder, LOWEST_LEVEL, NO_GRACE_MILLIS, clock::get);
    }

    private static Connection rawConnection(Path folder) throws SQLException {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + folder.resolve(SqliteCellStore.FILE_NAME));
        return source.getConnection();
    }
}
