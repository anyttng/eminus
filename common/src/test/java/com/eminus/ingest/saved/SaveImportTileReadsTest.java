package com.eminus.ingest.saved;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class SaveImportTileReadsTest {
    private static final int WORKERS = 2;
    private static final int LOWEST_STORED_LEVEL = 0;
    private static final int MIN_BLOCK_Y = -64;
    private static final int MIN_SECTION_Y = -4;
    private static final int RADIUS_CHUNKS = 64;
    private static final int VIEW_CHUNKS = 2;
    private static final int PLAYER_CHUNK = 8;
    private static final int TILE_MIN_CHUNK = 32;
    private static final int WANTED_CHUNK = 40;
    private static final int COLUMN_AND_NEIGHBORS = 9;
    private static final long SEED = 0x5EED_1234_ABCDL;
    private static final long AWAIT_MILLIS = 10_000L;
    private static final long POLL_MILLIS = 10L;
    private static final long SETTLE_MILLIS = 300L;
    private static final int REGION_COLUMNS = TILE_MIN_CHUNK * TILE_MIN_CHUNK;
    private static final String X_KEY = "x";
    private static final String Z_KEY = "z";

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
    void aColumnTheSaveLacksCostsOneRead() throws InterruptedException {
        SaveImport importer = importer(new OneRegion(false));

        importer.tick(PLAYER_CHUNK, PLAYER_CHUNK, RADIUS_CHUNKS, VIEW_CHUNKS);

        assertTrue(ranATile(importer), "the tile with the wanted column never ran");
        SaveImport.Tally tally = importer.tally();
        assertEquals(1, tally.reads());
        assertEquals(0, tally.parsed());
        assertEquals(1, tally.notSaved());
        importer.stop();
    }

    @Test
    void aColumnTheSaveHoldsCostsItsOwnReadAndItsNeighbors() throws InterruptedException {
        SaveImport importer = importer(new OneRegion(true));

        importer.tick(PLAYER_CHUNK, PLAYER_CHUNK, RADIUS_CHUNKS, VIEW_CHUNKS);

        assertTrue(ranATile(importer), "the tile with the wanted column never ran");
        SaveImport.Tally tally = importer.tally();
        assertEquals(COLUMN_AND_NEIGHBORS, tally.reads());
        assertEquals(COLUMN_AND_NEIGHBORS, tally.parsed());
        assertEquals(1, tally.imported());
        importer.stop();
    }

    @Test
    void aTileWithEveryColumnCoveredIsCheckedOnce() throws InterruptedException {
        SaveImport importer = importer(new OneRegion(false), false);
        importer.tick(PLAYER_CHUNK, PLAYER_CHUNK, RADIUS_CHUNKS, VIEW_CHUNKS);
        assertTrue(checked(importer, REGION_COLUMNS), "the first plan never checked the region's tiles");

        importer.tick(-PLAYER_CHUNK, PLAYER_CHUNK, RADIUS_CHUNKS, VIEW_CHUNKS);
        Thread.sleep(SETTLE_MILLIS);

        assertEquals(REGION_COLUMNS, importer.tally().coverageChecks());
        importer.stop();
    }

    private SaveImport importer(SaveSource source) {
        return importer(source, true);
    }

    private SaveImport importer(SaveSource source, boolean oneWanted) {
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        for (int chunkZ = 0; chunkZ < 2 * TILE_MIN_CHUNK; chunkZ++) {
            for (int chunkX = 0; chunkX < 2 * TILE_MIN_CHUNK; chunkX++) {
                if (!oneWanted || chunkX != WANTED_CHUNK || chunkZ != WANTED_CHUNK) {
                    coverage.cover(chunkX, chunkZ);
                }
            }
        }

        WorkService<SectionPyramid> service = harness.register("import", SectionPyramid::new);
        return new SaveImport(source, ingest(service, coverage), coverage, service, SEED);
    }

    private static boolean checked(SaveImport importer, int columns) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);
        while (System.nanoTime() < deadline) {
            if (importer.tally().coverageChecks() >= columns) {
                return true;
            }

            Thread.sleep(POLL_MILLIS);
        }

        return false;
    }

    private static boolean ranATile(SaveImport importer) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);
        while (System.nanoTime() < deadline) {
            if (importer.tally().tiles() > 0) {
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

    private record OneRegion(boolean saved) implements SaveSource {
        @Override
        public LongSet regions() {
            return new LongOpenHashSet(new long[] {ChunkPos.asLong(1, 1)});
        }

        @Override
        public CompletableFuture<Optional<CompoundTag>> read(int chunkX, int chunkZ) {
            if (!saved) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            CompoundTag tag = new CompoundTag();
            tag.putInt(X_KEY, chunkX);
            tag.putInt(Z_KEY, chunkZ);
            return CompletableFuture.completedFuture(Optional.of(tag));
        }

        @Override
        public @Nullable SavedChunk parse(CompoundTag chunkTag) {
            return IngestColumns.stoneChunk(chunkTag.getInt(X_KEY), chunkTag.getInt(Z_KEY), MIN_SECTION_Y);
        }

        @Override
        public boolean hasSky() {
            return true;
        }
    }
}
