package com.eminus.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Cell;
import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellHandle;
import com.eminus.ingest.SectionPyramid;
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
    private static final long NO_GRACE_MILLIS = 0L;
    private static final int RAISED_STORED_LEVEL = 1;
    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = -2;
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
        openRuntime(LOWEST_STORED_LEVEL);
        harness.run(() -> {
            CellHandle handle = runtime.cells().open(KEY);
            handle.withCell(cell -> cell.set(1, 2, 3, ENTRY));
            handle.markDirty();
            runtime.cells().release(handle);
        });

        runtime.close();

        assertEquals(ENTRY, storedEntry());
    }

    @Test
    void closingTheRuntimeWritesACellThatIsStillHeld() {
        openRuntime(LOWEST_STORED_LEVEL);
        harness.run(() -> {
            CellHandle handle = runtime.cells().open(KEY);
            handle.withCell(cell -> cell.set(1, 2, 3, ENTRY));
            handle.markDirty();
        });

        runtime.close();

        assertEquals(ENTRY, storedEntry());
    }

    @Test
    void aRuntimeRaisedWithinTheGraceCoversColumnsFromTheLevelItsStoreStillHolds() {
        try (CellStore finest = SqliteCellStore.open(folder, LOWEST_STORED_LEVEL, NO_GRACE_MILLIS, clock::get)) {
            finest.write(Cell.blank(KEY));
        }

        openRuntime(RAISED_STORED_LEVEL);
        runtime.coverage().cover(CHUNK_X, CHUNK_Z);
        runtime.coverage().persist(CHUNK_X, CHUNK_Z);
        runtime.close();

        Map<Long, Integer> columns = new HashMap<>();
        try (CellStore reopened = SqliteCellStore.open(folder, LOWEST_STORED_LEVEL, NO_GRACE_MILLIS, clock::get)) {
            reopened.readColumns(columns::put);
        }

        assertEquals(Map.of(ColumnCoverage.pack(CHUNK_X, CHUNK_Z), LOWEST_STORED_LEVEL), columns);
    }

    private void openRuntime(int lowestStoredLevel) {
        runtime = new DimensionRuntime(
                new WorldIdentity(WORLD, SEED, DIMENSION), folder, new CellFrame(MIN_BLOCK_Y), lowestStoredLevel);
        runtime.createFolder();
        runtime.openStore(clock::get);
        runtime.openCells(parked.register("save", 1, WorkService.UNLIMITED, () -> null),
                parked.register("ingest", 1, WorkService.UNLIMITED, SectionPyramid::new), clock::get);
    }

    private long storedEntry() {
        try (CellStore reopened = SqliteCellStore.open(folder, LOWEST_STORED_LEVEL, NO_GRACE_MILLIS, clock::get)) {
            Cell cell = reopened.read(KEY);
            assertNotNull(cell);
            return cell.get(1, 2, 3);
        }
    }
}
