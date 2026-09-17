package com.eminus.mesh;

import java.util.Comparator;
import java.util.PriorityQueue;

import org.jspecify.annotations.Nullable;

public final class MeshQueue {
    private static final Comparator<Waiting> ORDER = (first, second) -> {
        int byPriority = Float.compare(second.task().priority(), first.task().priority());
        if (byPriority != 0) {
            return byPriority;
        }

        int byRetry = Boolean.compare(first.task().retried(), second.task().retried());
        return byRetry != 0 ? byRetry : Long.compare(first.arrival(), second.arrival());
    };

    private final PriorityQueue<Waiting> waiting = new PriorityQueue<>(ORDER);

    private long arrivals;

    private record Waiting(MeshTask task, long arrival) {
    }

    public synchronized void add(MeshTask task) {
        waiting.add(new Waiting(task, arrivals++));
    }

    public synchronized @Nullable MeshTask poll() {
        Waiting next = waiting.poll();
        return next == null ? null : next.task();
    }

    public synchronized int size() {
        return waiting.size();
    }

    public synchronized void clear() {
        waiting.clear();
    }
}
