package com.eminus.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import com.eminus.VanillaBootstrap;

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

    private static WorldIdentity identity(String dimension) {
        return new WorldIdentity("New World", SEED, dimension);
    }
}
