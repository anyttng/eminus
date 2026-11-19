package com.eminus.ingest.saved;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.CellFrame;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;
import com.eminus.cell.cache.CellCache;
import com.eminus.ingest.CellMerger;
import com.eminus.ingest.IngestService;
import com.eminus.ingest.SectionPyramid;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkService;
import com.eminus.work.WorkerHarness;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class SaveImportViewTest {
    private static final int WORKERS = 2;
    private static final int LOWEST_STORED_LEVEL = 0;
    private static final int MIN_BLOCK_Y = -64;
    private static final int RADIUS_CHUNKS = 64;
    private static final int VIEW_CHUNKS = 2;
    private static final int NARROW_VIEW_CHUNKS = 1;
    private static final int REGION_MIN_CHUNK = 32;
    private static final int PLAYER_CHUNK = 40;
    private static final int MOVED_PLAYER_CHUNK_X = 36;
    private static final int NUDGED_PLAYER_CHUNK_X = 41;
    private static final int OTHER_REGION_CHUNK_X = -8;
    private static final int HELD_CHUNK_X = 42;
    private static final int OUTSIDE_CHUNK = 34;
    private static final int DISC_VIEW_CHUNKS = 4;
    private static final int RING_CHUNK = 44;
    private static final int LIVE_CHUNK_Z = 43;
    private static final int VIEW_SIDE = 2 * VIEW_CHUNKS + 1;
    private static final int COLUMNS_OUTSIDE_THE_VIEW = REGION_MIN_CHUNK * REGION_MIN_CHUNK - VIEW_SIDE * VIEW_SIDE;
    private static final long SEED = 0x5EED_1234_ABCDL;
    private static final long AWAIT_MILLIS = 10_000L;
    private static final long POLL_MILLIS = 10L;
    private static final long SETTLE_MILLIS = 300L;

    private final WorkerHarness harness = new WorkerHarness(WORKERS);

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @AfterEach
    void stopTheHarness() {
        harness.close();
    }

    @Test
    void aColumnLeavingAShrunkViewIsRead() throws InterruptedException {
        SaveImport importer = importer(false);
        scanWithTheColumnInView(importer);

        boolean read = until(() -> {
            importer.tick(PLAYER_CHUNK, PLAYER_CHUNK, RADIUS_CHUNKS, NARROW_VIEW_CHUNKS);
            return importer.tally().reads() > 0;
        }, AWAIT_MILLIS);

        assertTrue(read, "the column that left the shrunk view was never read");
        assertEquals(1, importer.tally().notSaved());
        importer.stop();
    }

    @Test
    void aColumnThePlayerLeavesBehindIsRead() throws InterruptedException {
        SaveImport importer = importer(false);
        scanWithTheColumnInView(importer);

        boolean read = until(() -> {
            importer.tick(MOVED_PLAYER_CHUNK_X, PLAYER_CHUNK, RADIUS_CHUNKS, VIEW_CHUNKS);
            return importer.tally().reads() > 0;
        }, AWAIT_MILLIS);

        assertTrue(read, "the column the view moved off was never read");
        assertEquals(1, importer.tally().notSaved());
        importer.stop();
    }

    @Test
    void aColumnStillInTheMovedViewIsNotRead() throws InterruptedException {
        SaveImport importer = importer(false);
        scanWithTheColumnInView(importer);

        until(() -> {
            importer.tick(NUDGED_PLAYER_CHUNK_X, PLAYER_CHUNK, RADIUS_CHUNKS, VIEW_CHUNKS);
            return false;
        }, SETTLE_MILLIS);

        assertEquals(0, importer.tally().reads());
        importer.stop();
    }

    @Test
    void aPlanForAnotherRegionReadsTheColumnATileHeldBack() throws InterruptedException {
        SaveImport importer = importer(true);
        importer.tick(PLAYER_CHUNK, PLAYER_CHUNK, RADIUS_CHUNKS, VIEW_CHUNKS);
        assertTrue(until(() -> importer.tally().tiles() > 0, AWAIT_MILLIS), "the tile never ran");
        assertEquals(1, importer.tally().reads());

        importer.tick(OTHER_REGION_CHUNK_X, PLAYER_CHUNK, RADIUS_CHUNKS, VIEW_CHUNKS);

        assertTrue(until(() -> importer.tally().reads() > 1, AWAIT_MILLIS),
                "the plan skipped the column its finished tile had held back");
        importer.stop();
    }

    @Test
    void aColumnOnTheRingTheLivePathNeverTakesIsReadStandingStill() throws InterruptedException {
        SaveImport importer = importer((chunkX, chunkZ) -> chunkX == RING_CHUNK && chunkZ == RING_CHUNK);

        boolean read = until(() -> {
            importer.tick(PLAYER_CHUNK, PLAYER_CHUNK, RADIUS_CHUNKS, DISC_VIEW_CHUNKS);
            return importer.tally().reads() > 0;
        }, AWAIT_MILLIS);

        assertTrue(read, "the column sent without its eight neighbors was never read");
        assertEquals(1, importer.tally().notSaved());
        importer.stop();
    }

    @Test
    void aColumnTheLivePathTakesIsNotReadStandingStill() throws InterruptedException {
        SaveImport importer = importer((chunkX, chunkZ) -> chunkX == RING_CHUNK && chunkZ == LIVE_CHUNK_Z);

        until(() -> {
            importer.tick(PLAYER_CHUNK, PLAYER_CHUNK, RADIUS_CHUNKS, DISC_VIEW_CHUNKS);
            return false;
        }, SETTLE_MILLIS);

        assertEquals(0, importer.tally().reads());
        importer.stop();
    }

    private static void scanWithTheColumnInView(SaveImport importer) throws InterruptedException {
        importer.tick(PLAYER_CHUNK, PLAYER_CHUNK, RADIUS_CHUNKS, VIEW_CHUNKS);
        assertTrue(until(() -> importer.tally().coverageChecks() >= COLUMNS_OUTSIDE_THE_VIEW, AWAIT_MILLIS),
                "the first plan never checked the region's tiles");
        assertEquals(0, importer.tally().reads());
    }

    private static boolean until(BooleanSupplier reached, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < deadline) {
            if (reached.getAsBoolean()) {
                return true;
            }

            Thread.sleep(POLL_MILLIS);
        }

        return false;
    }

    private interface Uncovered {
        boolean at(int chunkX, int chunkZ);
    }

    private SaveImport importer(boolean oneOutsideTheView) {
        return importer((chunkX, chunkZ) -> (chunkX == HELD_CHUNK_X && chunkZ == PLAYER_CHUNK)
                || (oneOutsideTheView && chunkX == OUTSIDE_CHUNK && chunkZ == OUTSIDE_CHUNK));
    }

    private SaveImport importer(Uncovered uncovered) {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        for (int chunkZ = 0; chunkZ < 2 * REGION_MIN_CHUNK; chunkZ++) {
            for (int chunkX = 0; chunkX < 2 * REGION_MIN_CHUNK; chunkX++) {
                if (!uncovered.at(chunkX, chunkZ)) {
                    coverage.cover(chunkX, chunkZ);
                }
            }
        }

        WorkService<SectionPyramid> service = harness.register("import", SectionPyramid::new);
        return new SaveImport(new EmptyRegion(), ingest(service, coverage), coverage, service, SEED);
    }

    private static IngestService ingest(WorkService<SectionPyramid> service, ColumnCoverage coverage) {
        AtomicLong clock = new AtomicLong();
        CellCache cells = new CellCache(new FakeCellStore(), handle -> { }, clock::get);
        CellMerger merger = new CellMerger(cells, new CellFrame(MIN_BLOCK_Y), LOWEST_STORED_LEVEL,
                (handle, faceMask, edgeMask, occupancy, sequence) -> cells.release(handle));
        return new IngestService(service, new StateTable(new Dictionary<>((id, value) -> { })),
                new Dictionary<>((id, value) -> { }), merger, coverage,
                (handle, faceMask, edgeMask, occupancy, sequence) -> cells.release(handle));
    }

    private static final class EmptyRegion implements SaveSource {
        @Override
        public LongSet regions() {
            return new LongOpenHashSet(new long[] {ChunkPos.asLong(1, 1)});
        }

        @Override
        public CompletableFuture<Optional<CompoundTag>> read(int chunkX, int chunkZ) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public @Nullable SavedChunk parse(CompoundTag chunkTag) {
            return null;
        }

        @Override
        public boolean hasSky() {
            return true;
        }
    }
}
