package com.eminus.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class WorkerPoolTest {
    private static final Object SCRATCH = new Object();
    private static final long JOIN_MILLIS = 10_000L;

    @Test
    void workersAreDaemonThreadsBelowNormalPriority() {
        WorkerPool pool = WorkerPool.start(1);
        WorkService<Object> service = pool.register("probe", 1, WorkService.UNLIMITED, Object::new);
        Set<Thread> seen = ConcurrentHashMap.newKeySet();

        service.enqueue(scratch -> seen.add(Thread.currentThread()));
        service.stop(ShutdownMode.DRAIN);
        pool.shutdown();

        Thread worker = seen.iterator().next();
        assertTrue(worker.isDaemon());
        assertEquals(WorkerPool.THREAD_PRIORITY, worker.getPriority());
        assertTrue(worker.getName().startsWith(WorkerPool.THREAD_NAME_PREFIX));
    }

    @Test
    void aThrowingJobLeavesTheWorkerAlive() {
        WorkerPool pool = WorkerPool.start(1);
        WorkService<Object> service = pool.register("throwing", 1, WorkService.UNLIMITED, Object::new);
        AtomicInteger ran = new AtomicInteger();

        service.enqueue(scratch -> {
            throw new IllegalStateException("expected");
        });
        service.enqueue(scratch -> ran.incrementAndGet());
        service.stop(ShutdownMode.DRAIN);
        pool.shutdown();

        assertEquals(1, ran.get());
    }

    @Test
    void drainRunsEverythingThatWasQueued() {
        WorkerPool pool = WorkerPool.start(4);
        WorkService<Object> service = pool.register("drained", 1, WorkService.UNLIMITED, Object::new);
        AtomicInteger ran = new AtomicInteger();

        for (int job = 0; job < 500; job++) {
            service.enqueue(scratch -> ran.incrementAndGet());
        }

        service.stop(ShutdownMode.DRAIN);
        pool.shutdown();

        assertEquals(500, ran.get());
    }

    @Test
    void drainRunsTheQueueInTheOrderItWasFilled() {
        WorkerPool pool = WorkerPool.start(1);
        WorkService<Object> service = pool.register("ordered", 1, WorkService.UNLIMITED, Object::new);
        List<Integer> ran = new ArrayList<>();

        for (int job = 0; job < 100; job++) {
            int number = job;
            service.enqueue(scratch -> ran.add(number));
        }

        service.stop(ShutdownMode.DRAIN);
        pool.shutdown();

        assertEquals(IntStream.range(0, 100).boxed().toList(), ran);
    }

    @Test
    void inlineRunsTheRemainderOnTheStoppingThread() {
        WorkerPool pool = WorkerPool.start(0);
        WorkService<Object> service = pool.register("inline", 1, WorkService.UNLIMITED, Object::new);
        Set<Thread> seen = ConcurrentHashMap.newKeySet();
        AtomicInteger ran = new AtomicInteger();

        for (int job = 0; job < 10; job++) {
            service.enqueue(scratch -> {
                seen.add(Thread.currentThread());
                ran.incrementAndGet();
            });
        }

        service.stop(ShutdownMode.INLINE);
        pool.shutdown();

        assertEquals(10, ran.get());
        assertEquals(Set.of(Thread.currentThread()), seen);
    }

    @Test
    void aServiceNeverExceedsItsLimiter() {
        WorkerPool pool = WorkerPool.start(4);
        WorkService<Object> service = pool.register("limited", 1, 1, Object::new);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        for (int job = 0; job < 500; job++) {
            service.enqueue(scratch -> {
                peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                inFlight.decrementAndGet();
            });
        }

        service.stop(ShutdownMode.DRAIN);
        pool.shutdown();

        assertEquals(1, peak.get());
    }

    @Test
    void oneScratchIsCreatedPerWorkerAndService() {
        WorkerPool pool = WorkerPool.start(1);
        AtomicInteger created = new AtomicInteger();
        WorkService<Object> service = pool.register("scratched", 1, WorkService.UNLIMITED, () -> {
            created.incrementAndGet();
            return SCRATCH;
        });
        Set<Object> seen = ConcurrentHashMap.newKeySet();

        for (int job = 0; job < 200; job++) {
            service.enqueue(seen::add);
        }

        service.stop(ShutdownMode.DRAIN);
        pool.shutdown();

        assertEquals(1, created.get());
        assertSame(SCRATCH, seen.iterator().next());
    }

    @Test
    void aStoppedServiceRefusesNewJobs() {
        WorkerPool pool = WorkerPool.start(1);
        WorkService<Object> service = pool.register("stopped", 1, WorkService.UNLIMITED, Object::new);
        AtomicInteger ran = new AtomicInteger();

        service.stop(ShutdownMode.DRAIN);
        service.enqueue(scratch -> ran.incrementAndGet());
        pool.shutdown();

        assertEquals(0, ran.get());
    }

    @Test
    void discardDropsTheQueuedJobsAndReturns() {
        WorkerPool pool = WorkerPool.start(0);
        WorkService<Object> service = pool.register("discarded", 1, WorkService.UNLIMITED, Object::new);
        AtomicInteger ran = new AtomicInteger();

        for (int job = 0; job < 500; job++) {
            service.enqueue(scratch -> ran.incrementAndGet());
        }

        service.stop(ShutdownMode.DISCARD);
        pool.shutdown();

        assertEquals(0, ran.get());
    }

    @Test
    void growingThePoolSpreadsWorkOverTheNewThreads() {
        WorkerPool pool = WorkerPool.start(1);
        WorkService<Object> service = pool.register("grown", 1, WorkService.UNLIMITED, Object::new);

        pool.resize(3);
        Set<Thread> seen = holdEveryWorker(service, 3);
        service.stop(ShutdownMode.DRAIN);
        pool.shutdown();

        assertEquals(3, pool.size());
        assertEquals(Set.of(WorkerPool.THREAD_NAME_PREFIX + 1, WorkerPool.THREAD_NAME_PREFIX + 2,
                WorkerPool.THREAD_NAME_PREFIX + 3), names(seen));
    }

    @Test
    void shrinkingThePoolRetiresTheHighestNumberedWorkers() {
        WorkerPool pool = WorkerPool.start(3);
        WorkService<Object> service = pool.register("shrunk", 1, WorkService.UNLIMITED, Object::new);
        Set<Thread> before = holdEveryWorker(service, 3);

        pool.resize(1);
        for (Thread worker : before) {
            if (!worker.getName().equals(WorkerPool.THREAD_NAME_PREFIX + 1)) {
                join(worker);
                assertFalse(worker.isAlive());
            }
        }

        Set<Thread> after = ConcurrentHashMap.newKeySet();
        for (int job = 0; job < 100; job++) {
            service.enqueue(scratch -> after.add(Thread.currentThread()));
        }
        service.stop(ShutdownMode.DRAIN);
        pool.shutdown();

        assertEquals(1, pool.size());
        assertEquals(Set.of(WorkerPool.THREAD_NAME_PREFIX + 1), names(after));
    }

    @Test
    void aRetiredWorkerFinishesTheJobItHolds() {
        WorkerPool pool = WorkerPool.start(1);
        WorkService<Object> service = pool.register("retired", 1, WorkService.UNLIMITED, Object::new);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicInteger ran = new AtomicInteger();

        service.enqueue(scratch -> {
            worker.set(Thread.currentThread());
            started.countDown();
            WorkerHarness.await(release);
            ran.incrementAndGet();
        });
        WorkerHarness.await(started);
        pool.resize(0);
        assertTrue(worker.get().isAlive());

        release.countDown();
        join(worker.get());
        pool.shutdown();

        assertEquals(1, ran.get());
        assertEquals(0, pool.size());
        assertFalse(worker.get().isAlive());
    }

    @Test
    void shutdownJoinsEveryWorkerThread() {
        WorkerPool pool = WorkerPool.start(4);
        WorkService<Object> service = pool.register("joined", 1, WorkService.UNLIMITED, Object::new);
        Set<Thread> seen = ConcurrentHashMap.newKeySet();

        for (int job = 0; job < 500; job++) {
            service.enqueue(scratch -> seen.add(Thread.currentThread()));
        }

        service.stop(ShutdownMode.DRAIN);
        pool.shutdown();

        assertFalse(seen.isEmpty());
        seen.forEach(worker -> assertFalse(worker.isAlive()));
    }

    private static Set<Thread> holdEveryWorker(WorkService<Object> service, int workers) {
        Set<Thread> seen = ConcurrentHashMap.newKeySet();
        CountDownLatch all = new CountDownLatch(workers);

        for (int job = 0; job < workers; job++) {
            service.enqueue(scratch -> {
                seen.add(Thread.currentThread());
                all.countDown();
                WorkerHarness.await(all);
            });
        }

        WorkerHarness.await(all);
        return seen;
    }

    private static Set<String> names(Set<Thread> threads) {
        return threads.stream().map(Thread::getName).collect(Collectors.toSet());
    }

    private static void join(Thread thread) {
        try {
            thread.join(JOIN_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
