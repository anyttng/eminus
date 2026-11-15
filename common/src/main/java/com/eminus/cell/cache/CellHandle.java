package com.eminus.cell.cache;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.LongSupplier;

import com.eminus.cell.Cell;
import com.eminus.store.CellRecord;
import com.eminus.store.CellStore;
import com.eminus.store.StoreException;

import org.jspecify.annotations.Nullable;

public final class CellHandle {
    public static final long DIRTY_CAP_MILLIS = 30_000L;
    public static final long RETRY_MILLIS = 5_000L;

    private static final long NO_RETRY = 0L;

    private final long key;
    private final LongSupplier clock;
    private final WriteLatch latch;
    private final ReentrantLock cellLock = new ReentrantLock();
    private final ReentrantLock saveLock = new ReentrantLock();

    private @Nullable Cell cell;
    private @Nullable Throwable loadFailure;
    private int references;
    private volatile boolean dirty;
    private long changes;
    private volatile long dirtySince;
    private boolean queued;
    private long retryAt = NO_RETRY;

    CellHandle(long key, LongSupplier clock, WriteLatch latch) {
        this.key = key;
        this.clock = clock;
        this.latch = latch;
    }

    public long key() {
        return key;
    }

    public <T> T withCell(Function<Cell, T> action) {
        Cell loaded = awaitLoaded();
        cellLock.lock();

        try {
            return action.apply(loaded);
        } finally {
            cellLock.unlock();
        }
    }

    public synchronized void markDirty() {
        changes++;
        if (dirty) {
            return;
        }

        dirty = true;
        dirtySince = clock.getAsLong();
    }

    public boolean dirty() {
        return dirty;
    }

    public void save(CellStore store) {
        saveLock.lock();

        try {
            Cell loaded;
            long encoded;
            synchronized (this) {
                if (!dirty || cell == null) {
                    return;
                }

                loaded = cell;
                encoded = changes;
            }

            if (!latch.claimWrite()) {
                return;
            }

            try {
                writeEncoded(store, loaded);
                saved(encoded);
                latch.succeeded();
            } catch (StoreException failure) {
                failed();
                latch.failed(key, failure);
            }
        } finally {
            unqueue();
            saveLock.unlock();
        }
    }

    private void writeEncoded(CellStore store, Cell loaded) {
        int occupancy;
        byte[] record;

        cellLock.lock();
        try {
            occupancy = loaded.occupancy();
            record = CellRecord.encode(loaded);
        } finally {
            cellLock.unlock();
        }

        store.write(key, occupancy, record);
    }

    private synchronized void saved(long encoded) {
        retryAt = NO_RETRY;
        if (changes == encoded) {
            dirty = false;
        } else {
            dirtySince = clock.getAsLong();
        }
    }

    private synchronized void failed() {
        retryAt = clock.getAsLong() + RETRY_MILLIS;
    }

    synchronized void discard() {
        dirty = false;
    }

    long dirtySince() {
        return dirtySince;
    }

    private synchronized void unqueue() {
        queued = false;
    }

    synchronized void publish(Cell loaded) {
        cell = loaded;
        notifyAll();
    }

    synchronized void fail(Throwable failure) {
        loadFailure = failure;
        notifyAll();
    }

    synchronized Cell awaitLoaded() {
        while (cell == null) {
            if (loadFailure != null) {
                throw new StoreException("Cell " + key + " could not be read.", loadFailure);
            }

            try {
                wait();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for cell " + key + " to load.");
            }
        }

        return cell;
    }

    synchronized boolean claimQueue() {
        if (queued || !dirty) {
            return false;
        }

        queued = true;
        return true;
    }

    synchronized boolean claimDue() {
        if (queued || !dirty) {
            return false;
        }

        long now = clock.getAsLong();
        boolean due = retryAt == NO_RETRY ? now - dirtySince >= DIRTY_CAP_MILLIS : now >= retryAt;
        if (!due) {
            return false;
        }

        queued = true;
        return true;
    }

    int references() {
        return references;
    }

    void acquire() {
        references++;
    }

    int release() {
        if (references == 0) {
            throw new IllegalStateException("Cell " + key + " was released more often than opened.");
        }

        return --references;
    }
}
