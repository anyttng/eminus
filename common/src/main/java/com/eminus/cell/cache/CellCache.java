package com.eminus.cell.cache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import com.eminus.cell.Cell;
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
    private final WriteLatch latch;

    public CellCache(CellStore store, CellSaver saves, LongSupplier clock) {
        this(store, saves, clock, new WriteFailureLog());
    }

    public CellCache(CellStore store, CellSaver saves, LongSupplier clock, WriteLatch.Report report) {
        this.store = store;
        this.saves = saves;
        this.clock = clock;
        this.latch = new WriteLatch(clock, report);
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
                    handle = new CellHandle(key, clock, latch);
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

        lock.lock();
        try {
            if (handle.release() > 0) {
                return;
            }

            if (!handle.dirty()) {
                handles.remove(handle.key());
                released.park(handle);
                return;
            }
        } finally {
            lock.unlock();
        }

        if (!latch.latched() && handle.claimQueue()) {
            saves.submit(handle);
        }
    }

    public void sweep() {
        boolean latched = latch.latched();
        List<CellHandle> unsaved = new ArrayList<>();
        List<CellHandle> unheld = new ArrayList<>();
        List<CellHandle> park = new ArrayList<>();
        List<CellHandle> dropped = List.of();

        lock.lock();
        try {
            for (CellHandle handle : handles.values()) {
                if (!handle.dirty()) {
                    if (handle.references() == 0) {
                        park.add(handle);
                    }
                } else if (latched && handle.references() == 0) {
                    unheld.add(handle);
                } else {
                    unsaved.add(handle);
                }
            }

            for (CellHandle handle : park) {
                handles.remove(handle.key());
                released.park(handle);
            }

            if (unheld.size() > WriteLatch.MAX_HELD_DIRTY) {
                unheld.sort(Comparator.comparingLong(CellHandle::dirtySince));
                dropped = unheld.subList(0, unheld.size() - WriteLatch.MAX_HELD_DIRTY);
                for (CellHandle handle : dropped) {
                    handles.remove(handle.key());
                }
            }
        } finally {
            lock.unlock();
        }

        if (!dropped.isEmpty()) {
            dropped.forEach(CellHandle::discard);
            latch.dropped(dropped.size());
        }

        unsaved.addAll(unheld.subList(dropped.size(), unheld.size()));
        if (latched) {
            submitProbe(unsaved);
            return;
        }

        boolean recovered = latch.takeRecovered();
        for (CellHandle handle : unsaved) {
            if (recovered ? handle.claimQueue() : handle.claimDue()) {
                saves.submit(handle);
            }
        }
    }

    private void submitProbe(List<CellHandle> unsaved) {
        if (!latch.probeDue()) {
            return;
        }

        for (CellHandle handle : unsaved) {
            if (handle.claimQueue()) {
                saves.submit(handle);
                return;
            }
        }
    }

    public boolean flush() {
        List<CellHandle> live;

        lock.lock();
        try {
            live = new ArrayList<>(handles.values());
        } finally {
            lock.unlock();
        }

        latch.probeNow();
        live.forEach(handle -> handle.save(store));
        return latch.flushed() && live.stream().noneMatch(CellHandle::dirty);
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
        } catch (Throwable failure) {
            forget(handle);
            handle.fail(failure);
            throw failure;
        }

        handle.publish(loaded == null ? Cell.blank(key) : loaded);
    }

    private void forget(CellHandle handle) {
        lock.lock();
        try {
            handles.remove(handle.key(), handle);
        } finally {
            lock.unlock();
        }
    }
}
