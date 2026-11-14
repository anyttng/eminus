package com.eminus.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Cell;
import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellHandle;
import com.eminus.ingest.IngestColumns;
import com.eminus.ingest.SectionPyramid;
import com.eminus.ingest.saved.SavedColumn;
import com.eminus.store.CellStore;
import com.eminus.store.SqliteCellStore;
import com.eminus.work.WorkService;
import com.eminus.work.WorkerHarness;
import com.eminus.work.WorkerPool;

import org.jspecify.annotations.Nullable;
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
    private static final int MIN_SECTION_Y = MIN_BLOCK_Y / SectionPyramid.SECTION_SIDE;
    private static final long BIOME_ZOOM_SEED = 0x5EED_1234_ABCDL;
    private static final int STORED_LEVELS = DetailLevel.MAX - LOWEST_STORED_LEVEL + 1;
    private static final String INGEST_SERVICE = "ingest";
    private static final long POLL_MILLIS = 1L;

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
            reopened.readColumns((chunk, lowestLevel, current) -> columns.put(chunk, lowestLevel));
        }

        assertEquals(Map.of(ColumnCoverage.pack(CHUNK_X, CHUNK_Z), LOWEST_STORED_LEVEL), columns);
    }

    @Test
    void closingTheRuntimeWaitsForASectionJobInsideItsMerge() throws InterruptedException {
        openRuntime(LOWEST_STORED_LEVEL, harness.register(INGEST_SERVICE, SectionPyramid::new));
        HeldMerge merge = holdTheFirstChange();
        IngestColumns.submit(runtime.ingest(), column());
        WorkerHarness.await(merge.entered());

        assertEquals(STORED_LEVELS, changesSeenByTheClose(merge),
                "the close returned before the section in flight had merged every level");
        assertNotNull(storedCell(coarsestCell()), "the coarsest level of the section in flight was not saved");
    }

    @Test
    void closingTheRuntimeWaitsForAnImportedColumnInsideItsMerge() throws InterruptedException {
        openRuntime(LOWEST_STORED_LEVEL);
        HeldMerge merge = holdTheFirstChange();
        SavedColumn column = column();
        harness.service().enqueue(scratch -> runtime.ingest().ingestColumn(column, new SectionPyramid()));
        WorkerHarness.await(merge.entered());

        assertEquals(STORED_LEVELS, changesSeenByTheClose(merge),
                "the close returned before the section in flight had merged every level");
        assertNotNull(storedCell(coarsestCell()), "the coarsest level of the section in flight was not saved");
    }

    private void openRuntime(int lowestStoredLevel) {
        openRuntime(lowestStoredLevel,
                parked.register(INGEST_SERVICE, 1, WorkService.UNLIMITED, SectionPyramid::new));
    }

    private void openRuntime(int lowestStoredLevel, WorkService<SectionPyramid> ingest) {
        runtime = new DimensionRuntime(
                new WorldIdentity(WORLD, SEED, DIMENSION), folder, new CellFrame(MIN_BLOCK_Y), lowestStoredLevel);
        runtime.createFolder();
        runtime.openStore(clock::get);
        runtime.openCells(parked.register("save", 1, WorkService.UNLIMITED, () -> null), ingest, clock::get);
    }

    private HeldMerge holdTheFirstChange() {
        HeldMerge merge = new HeldMerge(new CountDownLatch(1), new CountDownLatch(1), new AtomicInteger());
        runtime.listenTo((handle, faceMask, edgeMask, occupancy, sequence) -> {
            merge.changes().incrementAndGet();
            merge.entered().countDown();
            WorkerHarness.await(merge.letGo());
            runtime.cells().release(handle);
        });
        return merge;
    }

    private int changesSeenByTheClose(HeldMerge merge) throws InterruptedException {
        AtomicInteger seen = new AtomicInteger();
        Thread closer = new Thread(() -> {
            runtime.close();
            seen.set(merge.changes().get());
        });
        closer.setDaemon(true);
        closer.start();

        try {
            while (closer.getState() != Thread.State.WAITING && closer.getState() != Thread.State.TERMINATED) {
                Thread.sleep(POLL_MILLIS);
            }
        } finally {
            merge.letGo().countDown();
        }

        closer.join();
        return seen.get();
    }

    private long coarsestCell() {
        return runtime.frame().keyAt(DetailLevel.MAX, CHUNK_X * SectionPyramid.SECTION_SIDE,
                (MIN_SECTION_Y + IngestColumns.BOTTOM) * SectionPyramid.SECTION_SIDE,
                CHUNK_Z * SectionPyramid.SECTION_SIDE);
    }

    private static SavedColumn column() {
        return IngestColumns.stone(CHUNK_X, CHUNK_Z, MIN_SECTION_Y, BIOME_ZOOM_SEED);
    }

    private long storedEntry() {
        Cell cell = storedCell(KEY);
        assertNotNull(cell);
        return cell.get(1, 2, 3);
    }

    private @Nullable Cell storedCell(long key) {
        try (CellStore reopened = SqliteCellStore.open(folder, LOWEST_STORED_LEVEL, NO_GRACE_MILLIS, clock::get)) {
            return reopened.read(key);
        }
    }

    private record HeldMerge(CountDownLatch entered, CountDownLatch letGo, AtomicInteger changes) {
    }
}
