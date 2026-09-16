package com.eminus.store;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.eminus.cell.cache.CellHandle;
import com.eminus.cell.cache.CellSaver;
import com.eminus.work.WorkService;

public final class SaveService implements CellSaver {
    public static final int SOFT_CAP = 128;

    private final CellStore store;
    private final WorkService<Void> service;
    private final Queue<CellHandle> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();

    public SaveService(CellStore store, WorkService<Void> service) {
        this.store = store;
        this.service = service;
    }

    @Override
    public void submit(CellHandle handle) {
        queue.add(handle);
        pending.incrementAndGet();
        service.enqueue(scratch -> saveOne());

        while (pending.get() > SOFT_CAP) {
            if (!saveOne()) {
                return;
            }
        }
    }

    public void flush() {
        boolean more = true;
        while (more) {
            more = saveOne();
        }
    }

    public int pending() {
        return pending.get();
    }

    private boolean saveOne() {
        CellHandle handle = queue.poll();
        if (handle == null) {
            return false;
        }

        pending.decrementAndGet();
        handle.save(store);
        return true;
    }
}
