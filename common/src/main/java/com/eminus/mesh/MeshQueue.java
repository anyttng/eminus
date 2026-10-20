package com.eminus.mesh;

import java.util.Comparator;
import java.util.PriorityQueue;

import org.jspecify.annotations.Nullable;

public final class MeshQueue {
    static final double DELAY_NANOS_PER_PIXEL = 1.0e10;

    private static final double LONGEST_DELAY_NANOS = 1.0e15;
    private static final int RETRY_DELAYS = 2;
    private static final Comparator<Waiting> ORDER = Comparator.comparing((Waiting waiting) -> !waiting.inView())
            .thenComparingLong(Waiting::deadline)
            .thenComparingLong(Waiting::arrival);

    private final PriorityQueue<Waiting> waiting = new PriorityQueue<>(ORDER);

    private long arrivals;

    private record Waiting(MeshTask task, boolean inView, long deadline, long arrival) {
    }

    static long deadline(MeshTask task) {
        float priority = task.priority();
        double delay = priority > 0.0F ? DELAY_NANOS_PER_PIXEL / priority : DELAY_NANOS_PER_PIXEL * -priority;
        if (task.retried()) {
            delay *= RETRY_DELAYS;
        }

        return task.queued() + (long) Math.min(delay, LONGEST_DELAY_NANOS);
    }

    public synchronized void add(MeshTask task) {
        waiting.add(new Waiting(task, task.priority() > 0.0F, deadline(task), arrivals++));
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
