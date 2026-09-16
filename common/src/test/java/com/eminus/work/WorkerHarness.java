package com.eminus.work;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class WorkerHarness implements AutoCloseable {
    private static final String SERVICE_NAME = "test";
    private static final long AWAIT_MILLIS = 10_000L;

    private final WorkerPool pool;
    private final WorkService<Void> service;

    public WorkerHarness(int threadCount) {
        pool = WorkerPool.start(threadCount);
        service = pool.register(SERVICE_NAME, 1, WorkService.UNLIMITED, () -> null);
    }

    public WorkService<Void> service() {
        return service;
    }

    public <C> WorkService<C> register(String name, Supplier<C> scratchFactory) {
        return pool.register(name, 1, WorkService.UNLIMITED, scratchFactory);
    }

    public <T> T call(Supplier<T> body) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        service.enqueue(scratch -> {
            try {
                result.set(body.get());
            } catch (RuntimeException thrown) {
                failure.set(thrown);
            } finally {
                done.countDown();
            }
        });

        await(done);
        if (failure.get() != null) {
            throw failure.get();
        }

        return result.get();
    }

    public void run(Runnable body) {
        call(() -> {
            body.run();
            return null;
        });
    }

    @Override
    public void close() {
        service.stop(ShutdownMode.DRAIN);
        pool.shutdown();
    }

    public static void await(CountDownLatch latch) {
        try {
            if (!latch.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("A worker job did not finish within " + AWAIT_MILLIS + " ms.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a worker job.");
        }
    }
}
