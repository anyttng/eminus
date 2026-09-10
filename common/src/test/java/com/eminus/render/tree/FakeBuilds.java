package com.eminus.render.tree;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.eminus.cell.cache.CellHandle;

import org.jspecify.annotations.Nullable;

final class FakeBuilds implements TreeBuilds {
    record Call(long key, @Nullable CellHandle handle, int references) {
    }

    private static final long AWAIT_MILLIS = 10_000L;

    private final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();

    @Override
    public void build(long key, @Nullable CellHandle handle, int references) {
        calls.add(new Call(key, handle, references));
    }

    Call take() {
        Call call;

        try {
            call = calls.poll(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a build request.");
        }

        if (call == null) {
            throw new IllegalStateException("No build request arrived within " + AWAIT_MILLIS + " ms.");
        }

        return call;
    }

    boolean idle() {
        return calls.isEmpty();
    }
}
