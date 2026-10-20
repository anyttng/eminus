package com.eminus.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.CellKey;
import com.eminus.cell.cache.CellHandle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class EminusInstanceTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final long SEED = 8675309L;
    private static final int MIN_BLOCK_Y = -64;
    private static final int WORKER_THREADS = 1;
    private static final int LOWEST_STORED_LEVEL = 0;
    private static final long CELL_KEY = CellKey.pack(0, 0, 0, 0);
    private static final Duration OTHER_ACQUIRE_LIMIT = Duration.ofSeconds(5);
    private static final long POLL_MILLIS = 1L;

    @TempDir
    Path storeBase;

    private final AtomicLong clock = new AtomicLong();

    private EminusInstance instance;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @AfterEach
    void stopTheInstance() {
        if (instance != null) {
            instance.shutdown();
        }
    }

    @Test
    void oneRuntimeServesEveryHolderOfADimension() {
        start();

        DimensionRuntime first = instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);
        DimensionRuntime second = instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);

        assertSame(first, second);
        assertEquals(1, instance.runtimeCount());
    }

    @Test
    void eachDimensionGetsItsOwnRuntimeAndFolder() {
        start();

        DimensionRuntime overworld = instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);
        DimensionRuntime nether = instance.acquire(identity(NETHER), MIN_BLOCK_Y);

        assertEquals(2, instance.runtimeCount());
        assertTrue(Files.isDirectory(overworld.folder()));
        assertTrue(Files.isDirectory(nether.folder()));
        assertEquals(storeBase, overworld.folder().getParent());
    }

    @Test
    void theRuntimeCarriesTheDimensionFloor() {
        start();

        assertEquals(MIN_BLOCK_Y, instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y).frame().minBlockY());
    }

    @Test
    void aReleasedRuntimeIsClosedOnlyOnceItHasBeenIdleLongEnough() {
        start();
        DimensionRuntime runtime = instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);
        instance.release(runtime);

        clock.set(WorldCleaner.IDLE_CLOSE_MILLIS - 1);
        instance.closeIdleRuntimes();
        assertFalse(runtime.closed());
        assertEquals(1, instance.runtimeCount());

        clock.set(WorldCleaner.IDLE_CLOSE_MILLIS);
        instance.closeIdleRuntimes();
        assertTrue(runtime.closed());
        assertEquals(0, instance.runtimeCount());
    }

    @Test
    void aRuntimeStillHeldIsNeverClosed() {
        start();
        DimensionRuntime runtime = instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);
        instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);
        instance.release(runtime);

        clock.set(WorldCleaner.IDLE_CLOSE_MILLIS * 10);
        instance.closeIdleRuntimes();

        assertFalse(runtime.closed());
        assertEquals(1, instance.runtimeCount());
    }

    @Test
    void anIdleCloseHoldsUpNoAcquireOfAnotherDimension() throws InterruptedException {
        start();
        DimensionRuntime overworld = instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);
        CountDownLatch letGo = holdADirtyCell(overworld);
        Thread closer = null;

        try {
            closer = closeWhileTheCellIsHeld(overworld);

            DimensionRuntime nether = assertTimeoutPreemptively(OTHER_ACQUIRE_LIMIT,
                    () -> instance.acquire(identity(NETHER), MIN_BLOCK_Y));
            assertFalse(nether.closed());
        } finally {
            letGo.countDown();
            if (closer != null) {
                closer.join();
            }
        }

        assertTrue(overworld.closed());
    }

    @Test
    void anAcquireOfTheClosingDimensionWaitsForTheCloseAndOpensAFreshRuntime() throws InterruptedException {
        start();
        DimensionRuntime stale = instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);
        CountDownLatch letGo = holdADirtyCell(stale);
        AtomicReference<DimensionRuntime> fresh = new AtomicReference<>();
        Thread closer = null;
        Thread acquirer = null;

        try {
            closer = closeWhileTheCellIsHeld(stale);
            acquirer = startThread(() -> fresh.set(instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y)));
            awaitStalled(acquirer);

            assertNull(fresh.get());
        } finally {
            letGo.countDown();
            if (closer != null) {
                closer.join();
            }
        }

        acquirer.join();
        assertTrue(stale.closed());
        assertNotSame(stale, fresh.get());
        assertFalse(fresh.get().closed());
        assertEquals(1, instance.runtimeCount());
    }

    @Test
    void theSweepReachesTheCacheOfEveryOpenRuntime() {
        start();
        instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);

        assertDoesNotThrow(instance::sweepRuntimes);
    }

    @Test
    void releasingMoreOftenThanAcquiredIsRefused() {
        start();
        DimensionRuntime runtime = instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);
        instance.release(runtime);

        assertThrows(IllegalStateException.class, () -> instance.release(runtime));
    }

    @Test
    void shutdownClosesEveryRuntimeAndTakesNoMore() {
        start();
        DimensionRuntime runtime = instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);

        instance.shutdown();

        assertTrue(runtime.closed());
        assertEquals(0, instance.runtimeCount());
        assertThrows(IllegalStateException.class, () -> instance.acquire(identity(NETHER), MIN_BLOCK_Y));
    }

    @Test
    void resizingTheWorkersChangesTheLiveCount() {
        start();

        instance.resizeWorkers(WORKER_THREADS + 2);
        assertEquals(WORKER_THREADS + 2, instance.workerThreads());

        instance.resizeWorkers(WORKER_THREADS);
        assertEquals(WORKER_THREADS, instance.workerThreads());
    }

    @Test
    void aStoppedInstanceRefusesAResize() {
        start();
        instance.shutdown();

        assertThrows(IllegalStateException.class, () -> instance.resizeWorkers(WORKER_THREADS + 1));
    }

    @Test
    void theInstanceCarriesTheLevelItStartedWith() {
        start();

        assertEquals(LOWEST_STORED_LEVEL, instance.lowestStoredLevel());
    }

    @Test
    void aSecondShutdownIsASilentNoOp() {
        start();
        DimensionRuntime runtime = instance.acquire(identity(OVERWORLD), MIN_BLOCK_Y);
        instance.shutdown();

        assertDoesNotThrow(instance::shutdown);
        assertTrue(runtime.closed());
        assertEquals(0, instance.runtimeCount());
    }

    private void start() {
        instance = EminusInstance.start(storeBase, WORKER_THREADS, LOWEST_STORED_LEVEL, clock::get);
    }

    private CountDownLatch holdADirtyCell(DimensionRuntime runtime) throws InterruptedException {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch letGo = new CountDownLatch(1);
        instance.build().enqueue(scratch -> {
            CellHandle handle = runtime.cells().open(CELL_KEY);
            handle.markDirty();
            handle.withCell(cell -> {
                held.countDown();
                awaitQuietly(letGo);
                return null;
            });
        });

        held.await();
        return letGo;
    }

    private Thread closeWhileTheCellIsHeld(DimensionRuntime runtime) throws InterruptedException {
        instance.release(runtime);
        clock.set(WorldCleaner.IDLE_CLOSE_MILLIS);
        Thread closer = startThread(instance::closeIdleRuntimes);
        awaitStalled(closer);
        return closer;
    }

    private static Thread startThread(Runnable action) {
        Thread thread = new Thread(action);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void awaitStalled(Thread thread) throws InterruptedException {
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.BLOCKED) {
            Thread.sleep(POLL_MILLIS);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static WorldIdentity identity(String dimension) {
        return new WorldIdentity("New World", SEED, dimension);
    }
}
