package com.eminus.cell.cache;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.LongSupplier;

import com.eminus.Eminus;
import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.store.CellStore;
import com.eminus.store.StoreException;

public final class CellHandle {
    public static final long DIRTY_CAP_MILLIS = 30_000L;
    public static final long RETRY_MILLIS = 5_000L;

    private static final long NO_RETRY = 0L;

    private final long key;
    private final LongSupplier clock;
    private final ReentrantLock cellLock = new ReentrantLock();

    private Cell cell;
    private int references;
    private boolean dirty;
    private long dirtySince;
    private boolean queued;
    private long retryAt = NO_RETRY;
    private boolean failureLogged;

    CellHandle(long key, LongSupplier clock) {
        this.key = key;
        this.clock = clock;
    }

    public long key() {
        return key;
    }

    // The handle monitor is taken before this lock, never the other way round.
    public <T> T withCell(Function<Cell, T> action) {
        awaitLoaded();
        cellLock.lock();

        try {
            return action.apply(cell);
        } finally {
            cellLock.unlock();
        }
    }

    public synchronized void markDirty() {
        if (dirty) {
            return;
        }

        dirty = true;
        dirtySince = clock.getAsLong();
    }

    public synchronized boolean dirty() {
        return dirty;
    }

    public synchronized void save(CellStore store) {
        queued = false;
        if (!dirty || cell == null) {
            return;
        }

        cellLock.lock();

        try {
            store.write(cell);
            dirty = false;
            retryAt = NO_RETRY;
            failureLogged = false;
        } catch (StoreException failure) {
            retryAt = clock.getAsLong() + RETRY_MILLIS;
            if (!failureLogged) {
                failureLogged = true;
                Eminus.LOGGER.error("Could not save the cell at level {} ({}, {}, {}); it stays dirty.",
                        CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key), failure);
            }
        } finally {
            cellLock.unlock();
        }
    }

    synchronized void publish(Cell loaded) {
        cell = loaded;
        notifyAll();
    }

    synchronized void awaitLoaded() {
        while (cell == null) {
            try {
                wait();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for cell " + key + " to load.");
            }
        }
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
