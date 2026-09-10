package com.eminus.session;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import com.eminus.Eminus;
import com.eminus.cell.CellFrame;
import com.eminus.work.ShutdownMode;
import com.eminus.work.WorkService;
import com.eminus.work.WorkerPool;

public final class EminusInstance {
    public static final String INGEST_SERVICE = "ingest";
    public static final String SAVE_SERVICE = "save";
    public static final String BUILD_SERVICE = "build";

    public static final int INGEST_WEIGHT = 60;
    public static final int SAVE_WEIGHT = 30;
    public static final int BUILD_WEIGHT = 10;

    private final Path storeBase;
    private final int lowestStoredLevel;
    private final LongSupplier clock;
    private final WorkerPool pool;
    private final WorkService<Void> ingest;
    private final WorkService<Void> save;
    private final WorkService<Void> build;
    private final Map<WorldIdentity, DimensionRuntime> runtimes = new HashMap<>();
    private final WorldCleaner cleaner = new WorldCleaner(this);

    private boolean running = true;

    private EminusInstance(Path storeBase, int lowestStoredLevel, LongSupplier clock, WorkerPool pool) {
        this.storeBase = storeBase;
        this.lowestStoredLevel = lowestStoredLevel;
        this.clock = clock;
        this.pool = pool;
        ingest = pool.register(INGEST_SERVICE, INGEST_WEIGHT, WorkService.UNLIMITED, () -> null);
        save = pool.register(SAVE_SERVICE, SAVE_WEIGHT, WorkService.UNLIMITED, () -> null);
        build = pool.register(BUILD_SERVICE, BUILD_WEIGHT, WorkService.UNLIMITED, () -> null);
    }

    public static EminusInstance start(Path storeBase, int threadCount, int lowestStoredLevel, LongSupplier clock) {
        EminusInstance instance =
                new EminusInstance(storeBase, lowestStoredLevel, clock, WorkerPool.start(threadCount));
        instance.cleaner.start();
        Eminus.LOGGER.info("Session started on {} worker threads, store under {}", threadCount, storeBase);
        return instance;
    }

    public Path storeBase() {
        return storeBase;
    }

    public synchronized DimensionRuntime acquire(WorldIdentity identity, int minBlockY) {
        if (!running) {
            throw new IllegalStateException("The session is already stopped.");
        }

        DimensionRuntime runtime = runtimes.computeIfAbsent(identity, key -> open(key, minBlockY));
        runtime.acquire();
        return runtime;
    }

    public synchronized void release(DimensionRuntime runtime) {
        runtime.release(clock.getAsLong());
    }

    public void shutdown() {
        synchronized (this) {
            if (!running) {
                return;
            }

            running = false;
        }

        cleaner.stop();
        build.stop(ShutdownMode.DRAIN);
        ingest.stop(ShutdownMode.DRAIN);
        save.stop(ShutdownMode.INLINE);
        closeRuntimes();
        pool.shutdown();
        Eminus.LOGGER.info("Session stopped");
    }

    void sweepRuntimes() {
        List<DimensionRuntime> open;
        synchronized (this) {
            open = new ArrayList<>(runtimes.values());
        }

        for (DimensionRuntime runtime : open) {
            if (!runtime.closed()) {
                runtime.cells().sweep();
            }
        }
    }

    synchronized void closeIdleRuntimes() {
        long now = clock.getAsLong();
        runtimes.values().removeIf(runtime -> {
            if (runtime.references() > 0 || now - runtime.idleSince() < WorldCleaner.IDLE_CLOSE_MILLIS) {
                return false;
            }

            runtime.close();
            Eminus.LOGGER.info("Dimension runtime closed after idle: {}", runtime.identity().dimension());
            return true;
        });
    }

    synchronized int runtimeCount() {
        return runtimes.size();
    }

    private synchronized void closeRuntimes() {
        runtimes.values().forEach(DimensionRuntime::close);
        runtimes.clear();
    }

    private DimensionRuntime open(WorldIdentity identity, int minBlockY) {
        DimensionRuntime runtime = new DimensionRuntime(identity,
                StoreFolders.dimensionFolder(storeBase, identity), new CellFrame(minBlockY), lowestStoredLevel);
        runtime.createFolder();
        runtime.openStore();
        runtime.openCells(save, clock);
        Eminus.LOGGER.info("Dimension runtime opened for {} at {}", identity.dimension(), runtime.folder());
        return runtime;
    }
}
