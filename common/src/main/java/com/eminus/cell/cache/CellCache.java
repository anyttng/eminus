package com.eminus.cell.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import com.eminus.Eminus;
import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.store.CellStore;
import com.eminus.work.WorkerPool;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class CellCache implements CellAccess {
    private final CellStore store;
    private final CellSaver saves;
    private final LongSupplier clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final Long2ObjectOpenHashMap<CellHandle> handles = new Long2ObjectOpenHashMap<>();
    private final ReleasedCells released = new ReleasedCells();

    public CellCache(CellStore store, CellSaver saves, LongSupplier clock) {
        this.store = store;
        this.saves = saves;
        this.clock = clock;
    }

    @Override
    public CellHandle open(long key) {
        WorkerPool.requireWorkerThread("Opening a cell");

        CellHandle handle;
        boolean load = false;

        lock.lock();
        try {
            handle = handles.get(key);
            if (handle == null) {
                handle = released.take(key);
                if (handle == null) {
                    handle = new CellHandle(key, clock);
                    load = true;
                }

                handles.put(key, handle);
            }

            handle.acquire();
        } finally {
            lock.unlock();
        }

        if (load) {
            loadInto(handle);
        }

        handle.awaitLoaded();
        return handle;
    }

    @Override
    public void release(CellHandle handle) {
        WorkerPool.requireWorkerThread("Releasing a cell");

        boolean submit = false;

        lock.lock();
        try {
            if (handle.release() > 0) {
                return;
            }

            if (handle.dirty()) {
                submit = handle.claimQueue();
            } else {
                handles.remove(handle.key());
                released.park(handle);
            }
        } finally {
            lock.unlock();
        }

        if (submit) {
            saves.submit(handle);
        }
    }

    public void sweep() {
        List<CellHandle> due = new ArrayList<>();
        List<CellHandle> park = new ArrayList<>();

        lock.lock();
        try {
            for (CellHandle handle : handles.values()) {
                if (handle.references() == 0 && !handle.dirty()) {
                    park.add(handle);
                } else if (handle.claimDue()) {
                    due.add(handle);
                }
            }

            for (CellHandle handle : park) {
                handles.remove(handle.key());
                released.park(handle);
            }
        } finally {
            lock.unlock();
        }

        due.forEach(saves::submit);
    }

    public void flush() {
        List<CellHandle> live;

        lock.lock();
        try {
            live = new ArrayList<>(handles.values());
        } finally {
            lock.unlock();
        }

        live.forEach(handle -> handle.save(store));
    }

    public int liveCount() {
        lock.lock();
        try {
            return handles.size();
        } finally {
            lock.unlock();
        }
    }

    public int parkedCount() {
        lock.lock();
        try {
            return released.size();
        } finally {
            lock.unlock();
        }
    }

    private void loadInto(CellHandle handle) {
        long key = handle.key();
        Cell loaded;

        try {
            loaded = store.read(key);
        } catch (RuntimeException failure) {
            Eminus.LOGGER.error("Could not read the cell at level {} ({}, {}, {}); it opens blank.",
                    CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key), failure);
            loaded = null;
        }

        handle.publish(loaded == null ? Cell.blank(key) : loaded);
    }
}
