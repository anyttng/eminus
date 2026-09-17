package com.eminus.render.tree;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.eminus.cell.cache.CellHandle;

import org.jspecify.annotations.Nullable;

final class FakeBuilds implements TreeBuilds {
    record Call(long key, @Nullable CellHandle handle, int references, long request, float priority) {
    }

    record Release(CellHandle handle, int references) {
    }

    private static final long AWAIT_MILLIS = 10_000L;

    private final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();
    private final BlockingQueue<Release> releases = new LinkedBlockingQueue<>();

    private volatile int backlog;

    @Override
    public void build(long key, @Nullable CellHandle handle, int references, long request, float priority) {
        calls.add(new Call(key, handle, references, request, priority));
    }

    @Override
    public void release(CellHandle handle, int references) {
        releases.add(new Release(handle, references));
    }

    @Override
    public int backlog() {
        return backlog;
    }

    void backlog(int queued) {
        backlog = queued;
    }

    Call take() {
        return await(calls, "build request");
    }

    Release takeRelease() {
        return await(releases, "release");
    }

    boolean idle() {
        return calls.isEmpty();
    }

    private static <T> T await(BlockingQueue<T> queue, String what) {
        T taken;

        try {
            taken = queue.poll(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a " + what + ".");
        }

        if (taken == null) {
            throw new IllegalStateException("No " + what + " arrived within " + AWAIT_MILLIS + " ms.");
        }

        return taken;
    }
}
