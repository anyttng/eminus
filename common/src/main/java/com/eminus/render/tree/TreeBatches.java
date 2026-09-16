package com.eminus.render.tree;

import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

public final class TreeBatches {
    private final AtomicReference<TreeBatch> published = new AtomicReference<>();

    public @Nullable TreeBatch peek() {
        return published.get();
    }

    public @Nullable TreeBatch take() {
        return published.getAndSet(null);
    }

    boolean waiting() {
        return published.get() != null;
    }

    boolean offer(TreeBatch batch) {
        return published.compareAndSet(null, batch);
    }
}
