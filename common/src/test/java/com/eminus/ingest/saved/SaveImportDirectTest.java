package com.eminus.ingest.saved;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.CellFrame;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;
import com.eminus.cell.cache.CellCache;
import com.eminus.ingest.CellMerger;
import com.eminus.ingest.IngestColumns;
import com.eminus.ingest.IngestService;
import com.eminus.ingest.SectionPyramid;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkService;
import com.eminus.work.WorkerHarness;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.nbt.CompoundTag;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class SaveImportDirectTest {
    private static final int WORKERS = 2;
    private static final int LOWEST_STORED_LEVEL = 0;
    private static final int MIN_BLOCK_Y = -64;
    private static final int MIN_SECTION_Y = -4;
    private static final int RADIUS_SHORT_OF_FAR_CHUNK = 16;
    private static final int VIEW_CHUNKS = 2;
    private static final int FAR_CHUNK = 40;
    private static final long SEED = 0x5EED_1234_ABCDL;
    private static final long AWAIT_MILLIS = 10_000L;
    private static final long POLL_MILLIS = 10L;
    private static final long SETTLE_MILLIS = 300L;

    private final WorkerHarness harness = new WorkerHarness(WORKERS);
    private final AtomicInteger reads = new AtomicInteger();

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @AfterEach
    void stopTheHarness() {
        harness.close();
    }

    @Test
    void aSavedChunkIsImportedOnceItsNeighborsAreSavedWithNoReadOfTheSave() throws InterruptedException {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        SaveImport importer = importer(coverage);

        importer.saved(IngestColumns.stoneChunk(FAR_CHUNK, FAR_CHUNK, MIN_SECTION_Y));
        Thread.sleep(SETTLE_MILLIS);
        assertFalse(coverage.covers(FAR_CHUNK, FAR_CHUNK), "a chunk was imported before its neighbors were saved");

        saveRing(importer, FAR_CHUNK, FAR_CHUNK);

        assertTrue(covered(coverage, FAR_CHUNK, FAR_CHUNK), "the chunk was not imported from its snapshot");
        assertEquals(0, reads.get());
        importer.stop();
    }

    @Test
    void aSavedChunkInsideTheViewIsLeftToTheClient() throws InterruptedException {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        SaveImport importer = importer(coverage);

        importer.saved(IngestColumns.stoneChunk(1, 1, MIN_SECTION_Y));
        saveRing(importer, 1, 1);
        Thread.sleep(SETTLE_MILLIS);

        assertFalse(coverage.covers(1, 1));
        importer.stop();
    }

    private SaveImport importer(ColumnCoverage coverage) {
        WorkService<SectionPyramid> service = harness.register("import", SectionPyramid::new);
        SaveImport importer = new SaveImport(new EmptySave(), ingest(service, coverage), coverage, service, SEED);
        importer.tick(0, 0, RADIUS_SHORT_OF_FAR_CHUNK, VIEW_CHUNKS);
        return importer;
    }

    private static void saveRing(SaveImport importer, int chunkX, int chunkZ) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dz != 0) {
                    importer.saved(IngestColumns.stoneChunk(chunkX + dx, chunkZ + dz, MIN_SECTION_Y).asNeighbor());
                }
            }
        }
    }

    private static boolean covered(ColumnCoverage coverage, int chunkX, int chunkZ) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);
        while (System.nanoTime() < deadline) {
            if (coverage.covers(chunkX, chunkZ)) {
                return true;
            }

            Thread.sleep(POLL_MILLIS);
        }

        return false;
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

    private final class EmptySave implements SaveSource {
        @Override
        public LongSet regions() {
            return new LongOpenHashSet();
        }

        @Override
        public CompletableFuture<Optional<CompoundTag>> read(int chunkX, int chunkZ) {
            reads.incrementAndGet();
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
