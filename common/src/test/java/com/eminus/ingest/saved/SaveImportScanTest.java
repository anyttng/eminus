package com.eminus.ingest.saved;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.CellFrame;
import com.eminus.cell.ColumnCoverage;
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
class SaveImportScanTest {
    private static final int WORKERS = 2;
    private static final int LOWEST_STORED_LEVEL = 0;
    private static final int MIN_BLOCK_Y = -64;
    private static final int RADIUS_CHUNKS = 64;
    private static final int VIEW_CHUNKS = 2;
    private static final long SEED = 0x5EED_1234_ABCDL;
    private static final long AWAIT_SECONDS = 10L;
    private static final long SCAN_REACHES_COVERAGE_MILLIS = 300L;
    private static final long TICK_BUDGET_SECONDS = 2L;

    private final WorkerHarness harness = new WorkerHarness(WORKERS);
    private final CountDownLatch regionsListed = new CountDownLatch(1);
    private final CountDownLatch coverageHeld = new CountDownLatch(1);
    private final CountDownLatch releaseCoverage = new CountDownLatch(1);

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @AfterEach
    void stopTheHarness() {
        releaseCoverage.countDown();
        harness.close();
    }

    @Test
    void aTickDoesNotWaitForTheScanOfAPlan() throws InterruptedException {
        ColumnCoverage coverage = ColumnCoverage.everything();
        WorkService<SectionPyramid> service = harness.register("import", SectionPyramid::new);
        SaveImport importer = new SaveImport(new OneRegion(), ingest(service, coverage), coverage, service, SEED);
        Thread holder = holdCoverage(coverage);

        importer.tick(0, 0, RADIUS_CHUNKS, VIEW_CHUNKS);
        assertTrue(regionsListed.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the plan never listed the regions");
        Thread.sleep(SCAN_REACHES_COVERAGE_MILLIS);

        CountDownLatch ticked = new CountDownLatch(1);
        Thread game = new Thread(() -> {
            importer.tick(0, 0, RADIUS_CHUNKS, VIEW_CHUNKS);
            ticked.countDown();
        }, "game-thread-stand-in");
        game.setDaemon(true);
        game.start();
        boolean returned = ticked.await(TICK_BUDGET_SECONDS, TimeUnit.SECONDS);

        releaseCoverage.countDown();
        holder.join(TimeUnit.SECONDS.toMillis(AWAIT_SECONDS));
        importer.stop();
        assertTrue(returned, "tick waited for the scan while the scan stood inside a coverage read");
    }

    private Thread holdCoverage(ColumnCoverage coverage) throws InterruptedException {
        Thread holder = new Thread(() -> {
            synchronized (coverage) {
                coverageHeld.countDown();
                try {
                    releaseCoverage.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "coverage-holder");
        holder.setDaemon(true);
        holder.start();
        assertTrue(coverageHeld.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the coverage monitor was never taken");
        return holder;
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

    private final class OneRegion implements SaveSource {
        @Override
        public LongSet regions() {
            regionsListed.countDown();
            return new LongOpenHashSet(new long[] {ChunkPos.pack(0, 0)});
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
