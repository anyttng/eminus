package com.eminus.work;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.eminus.Eminus;

public final class WorkService<C> implements ServiceSelector.Selectable {
    public static final int UNLIMITED = Integer.MAX_VALUE;

    private final WorkerPool pool;
    private final String name;
    private final int weight;
    private final int maxInFlight;
    private final Supplier<C> scratchFactory;
    private final Queue<Job<C>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger();

    private volatile boolean accepting = true;

    WorkService(WorkerPool pool, String name, int weight, int maxInFlight, Supplier<C> scratchFactory) {
        this.pool = pool;
        this.name = name;
        this.weight = weight;
        this.maxInFlight = maxInFlight;
        this.scratchFactory = scratchFactory;
    }

    public String name() {
        return name;
    }

    @Override
    public int weight() {
        return weight;
    }

    @Override
    public boolean ready() {
        return !queue.isEmpty() && inFlight.get() < maxInFlight;
    }

    public void enqueue(Job<C> job) {
        if (!accepting) {
            Eminus.LOGGER.warn("Service {} is stopped; a job was dropped.", name);
            return;
        }

        queue.add(job);
        pool.signalWork();
    }

    public void stop(ShutdownMode mode) {
        accepting = false;

        if (mode == ShutdownMode.INLINE) {
            pool.runRemainderInline(this);
        }

        pool.awaitFinished(this);
    }

    Job<C> claim() {
        Job<C> job = queue.poll();
        if (job != null) {
            inFlight.incrementAndGet();
        }

        return job;
    }

    @SuppressWarnings("unchecked")
    void run(Job<?> job, Object scratch) {
        try {
            ((Job<C>) job).run((C) scratch);
        } catch (Throwable failure) {
            Eminus.LOGGER.error("A job of service {} threw.", name, failure);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    Object newScratch() {
        return scratchFactory.get();
    }

    boolean finished() {
        return queue.isEmpty() && inFlight.get() == 0;
    }
}
