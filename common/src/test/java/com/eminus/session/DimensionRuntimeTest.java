package com.eminus.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Cell;
import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellHandle;
import com.eminus.store.CellStore;
import com.eminus.store.SqliteCellStore;
import com.eminus.work.WorkService;
import com.eminus.work.WorkerHarness;
import com.eminus.work.WorkerPool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class DimensionRuntimeTest {
    private static final String WORLD = "New World";
    private static final String DIMENSION = "minecraft:overworld";
    private static final long SEED = 8675309L;
    private static final int MIN_BLOCK_Y = -64;
    private static final int LOWEST_STORED_LEVEL = 0;
    private static final int WORKER_THREADS = 1;
    private static final long KEY = CellKey.pack(0, 7, 0, 9);
    private static final long ENTRY = VoxelEntry.pack(11, 3, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0));

    @TempDir
    Path folder;

    private final AtomicLong clock = new AtomicLong();
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final WorkerPool parked = WorkerPool.start(0);

    private DimensionRuntime runtime;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @AfterEach
    void stopThePools() {
        harness.close();
        parked.shutdown();
    }

    @Test
    void closingTheRuntimeWritesACellReleasedButNotYetSaved() {
        openRuntime();
        harness.run(() -> {
            CellHandle handle = runtime.cells().open(KEY);
            handle.cell().set(1, 2, 3, ENTRY);
            handle.markDirty();
            runtime.cells().release(handle);
        });

        runtime.close();

        assertEquals(ENTRY, storedEntry());
    }

    @Test
    void closingTheRuntimeWritesACellThatIsStillHeld() {
        openRuntime();
        harness.run(() -> {
            CellHandle handle = runtime.cells().open(KEY);
            handle.cell().set(1, 2, 3, ENTRY);
            handle.markDirty();
        });

        runtime.close();

        assertEquals(ENTRY, storedEntry());
    }

    private void openRuntime() {
        runtime = new DimensionRuntime(
                new WorldIdentity(WORLD, SEED, DIMENSION), folder, new CellFrame(MIN_BLOCK_Y), LOWEST_STORED_LEVEL);
        runtime.createFolder();
        runtime.openStore();
        runtime.openCells(parked.register("save", 1, WorkService.UNLIMITED, () -> null), clock::get);
    }

    private long storedEntry() {
        try (CellStore reopened = SqliteCellStore.open(folder, LOWEST_STORED_LEVEL)) {
            Cell cell = reopened.read(KEY);
            assertNotNull(cell);
            return cell.get(1, 2, 3);
        }
    }
}
