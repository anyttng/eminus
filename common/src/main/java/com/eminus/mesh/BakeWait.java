package com.eminus.mesh;

import java.util.concurrent.atomic.AtomicInteger;

final class BakeWait implements Runnable {
    private final AtomicInteger pending = new AtomicInteger();
    private final Runnable whenAllBaked;

    BakeWait(Runnable whenAllBaked) {
        this.whenAllBaked = whenAllBaked;
    }

    @Override
    public void run() {
        if (pending.decrementAndGet() == 0) {
            whenAllBaked.run();
        }
    }

    void close(int awaited) {
        if (pending.addAndGet(awaited) == 0) {
            whenAllBaked.run();
        }
    }
}
