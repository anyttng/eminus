package com.eminus.work;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class WorkerPool {
    public static final String THREAD_NAME_PREFIX = "eminus-worker-";
    public static final int THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition work = lock.newCondition();
    private final Condition idle = lock.newCondition();
    private final List<WorkService<?>> services = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();
    private final ServiceSelector selector = new ServiceSelector();

    private boolean running = true;

    private WorkerPool() {
    }

    public static WorkerPool start(int threadCount) {
        WorkerPool pool = new WorkerPool();

        for (int index = 1; index <= threadCount; index++) {
            Thread thread = new Thread(pool.new Worker(), THREAD_NAME_PREFIX + index);
            thread.setDaemon(true);
            thread.setPriority(THREAD_PRIORITY);
            pool.threads.add(thread);
            thread.start();
        }

        return pool;
    }

    public <C> WorkService<C> register(String name, int weight, int maxInFlight, Supplier<C> scratchFactory) {
        WorkService<C> service = new WorkService<>(this, name, weight, maxInFlight, scratchFactory);

        lock.lock();
        try {
            services.add(service);
        } finally {
            lock.unlock();
        }

        return service;
    }

    public void shutdown() {
        lock.lock();
        try {
            running = false;
            work.signalAll();
        } finally {
            lock.unlock();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    void signalWork() {
        lock.lock();
        try {
            work.signal();
        } finally {
            lock.unlock();
        }
    }

    void runRemainderInline(WorkService<?> service) {
        Object scratch = null;

        while (true) {
            Job<?> job;
            lock.lock();
            try {
                job = service.claim();
            } finally {
                lock.unlock();
            }

            if (job == null) {
                return;
            }

            if (scratch == null) {
                scratch = service.newScratch();
            }

            service.run(job, scratch);
            signalFinished();
        }
    }

    void awaitFinished(WorkService<?> service) {
        lock.lock();
        try {
            while (!service.finished()) {
                if (!running) {
                    throw new IllegalStateException(
                            "Service " + service.name() + " still has work, but the pool is already shut down.");
                }

                work.signalAll();
                idle.await();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    private Claim awaitClaim() {
        lock.lock();
        try {
            while (running) {
                Claim claim = nextClaim();
                if (claim != null) {
                    return claim;
                }

                work.await();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }

        return null;
    }

    private Claim nextClaim() {
        int index = selector.pick(services);
        if (index == ServiceSelector.NONE) {
            return null;
        }

        WorkService<?> service = services.get(index);
        Job<?> job = service.claim();
        return job == null ? null : new Claim(service, job);
    }

    private void signalFinished() {
        lock.lock();
        try {
            idle.signalAll();
            work.signal();
        } finally {
            lock.unlock();
        }
    }

    private record Claim(WorkService<?> service, Job<?> job) {
    }

    private final class Worker implements Runnable {
        private final Map<WorkService<?>, Object> scratch = new HashMap<>();

        @Override
        public void run() {
            while (true) {
                Claim claim = awaitClaim();
                if (claim == null) {
                    return;
                }

                Object context = scratch.computeIfAbsent(claim.service(), WorkService::newScratch);
                claim.service().run(claim.job(), context);
                signalFinished();
            }
        }
    }
}
