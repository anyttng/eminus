package com.eminus.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.CellFrame;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;
import com.eminus.cell.cache.CellAccess;
import com.eminus.cell.cache.CellCache;
import com.eminus.cell.cache.CellHandle;
import com.eminus.ingest.saved.SavedChunk;
import com.eminus.ingest.saved.SavedColumn;
import com.eminus.store.FakeCellStore;
import com.eminus.store.StoreException;
import com.eminus.work.WorkService;
import com.eminus.work.WorkerHarness;

import net.minecraft.core.Holder;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class IngestServiceTest {
    private static final int WORKERS = 1;
    private static final int LOWEST_STORED_LEVEL = 0;
    private static final int MIN_BLOCK_Y = -64;
    private static final int MIN_SECTION_Y = MIN_BLOCK_Y / SectionPyramid.SECTION_SIDE;
    private static final int SECTIONS = 3;
    private static final int LIGHT_SECTIONS = SECTIONS + 2;
    private static final int BOTTOM = 0;
    private static final int TOP = 2;
    private static final int CHUNK_X = 0;
    private static final int CHUNK_Z = 0;
    private static final long SEED = 0x5EED_1234_ABCDL;

    private final WorkerHarness harness = new WorkerHarness(WORKERS);
    private final WorkService<SectionPyramid> service = harness.register("ingest", SectionPyramid::new);
    private final AtomicLong clock = new AtomicLong();
    private final CellCache cells = new CellCache(new FakeCellStore(), handle -> { }, clock::get);
    private final CellFrame frame = new CellFrame(MIN_BLOCK_Y);
    private final RefusingCells access = new RefusingCells();
    private final AtomicInteger announced = new AtomicInteger();

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @AfterEach
    void stopTheHarness() {
        harness.close();
    }

    @Test
    void aSectionWhoseMergeThrowsLeavesTheColumnUncoveredAndTheNextTriggerSubmitsItAgain() {
        ColumnCoverage coverage = new ColumnCoverage(LOWEST_STORED_LEVEL, (chunk, lowestLevel) -> { });
        IngestService ingest = ingest(coverage);
        SavedColumn column = column();
        long topCell = cellOf(TOP);
        access.refused = topCell;

        ingest.ingestSections(column, IngestService.pickSections(column), IngestTrigger.PACKET);
        drain();

        assertFalse(coverage.covers(CHUNK_X, CHUNK_Z), "a column with a section that never merged was covered");
        assertEquals(0, announced.get());

        access.refused = RefusingCells.NONE;
        access.opened.clear();
        ingest.ingestSections(column, IngestService.pickSections(column), IngestTrigger.PACKET);
        drain();

        assertTrue(access.opened.contains(cellOf(BOTTOM)), "the next trigger left out the section that had merged");
        assertTrue(access.opened.contains(topCell), "the next trigger left out the section that had thrown");
        assertTrue(coverage.covers(CHUNK_X, CHUNK_Z));
        assertEquals(1, announced.get());
    }

    @Test
    void aColumnWhosePersistThrowsIsStillAnnounced() {
        ColumnCoverage coverage = new ColumnCoverage(LOWEST_STORED_LEVEL, (chunk, lowestLevel) -> {
            throw new StoreException("The fake store refuses columns.", null);
        });
        IngestService ingest = ingest(coverage);
        SavedColumn column = column();

        ingest.ingestSections(column, IngestService.pickSections(column), IngestTrigger.PACKET);
        drain();

        assertTrue(coverage.covers(CHUNK_X, CHUNK_Z));
        assertEquals(1, announced.get(), "a column covered in memory was never announced");
    }

    private IngestService ingest(ColumnCoverage coverage) {
        CellMerger merger = new CellMerger(access, frame, LOWEST_STORED_LEVEL,
                (handle, faceMask, edgeMask, occupancy, sequence) -> cells.release(handle));
        return new IngestService(service, new StateTable(new Dictionary<>((id, value) -> { })),
                new Dictionary<>((id, value) -> { }), merger, coverage, new Announcements());
    }

    private void drain() {
        CountDownLatch done = new CountDownLatch(1);
        service.enqueue(pyramid -> done.countDown());
        WorkerHarness.await(done);
    }

    private long cellOf(int index) {
        return frame.keyAt(LOWEST_STORED_LEVEL, CHUNK_X * SectionPyramid.SECTION_SIDE,
                (MIN_SECTION_Y + index) * SectionPyramid.SECTION_SIDE, CHUNK_Z * SectionPyramid.SECTION_SIDE);
    }

    private static SavedColumn column() {
        LevelChunkSection[] sections = new LevelChunkSection[SECTIONS];
        sections[BOTTOM] = section(Blocks.STONE.defaultBlockState());
        sections[TOP] = section(Blocks.STONE.defaultBlockState());
        SavedChunk center = new SavedChunk(CHUNK_X, CHUNK_Z, MIN_SECTION_Y, sections, new DataLayer[LIGHT_SECTIONS],
                new DataLayer[LIGHT_SECTIONS], true, true, true, false);
        SavedChunk[] around = new SavedChunk[SavedColumn.NEIGHBOR_SIDE * SavedColumn.NEIGHBOR_SIDE];
        around[SavedColumn.neighborIndex(0, 0)] = center;
        return new SavedColumn(center, around, false, SEED);
    }

    private static LevelChunkSection section(BlockState state) {
        Holder<Biome> biome = Holder.direct(null);
        CrudeIncrementalIntIdentityHashBiMap<Holder<Biome>> biomeIds = CrudeIncrementalIntIdentityHashBiMap.create(1);
        biomeIds.add(biome);
        return new LevelChunkSection(
                new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, state, PalettedContainer.Strategy.SECTION_STATES),
                new PalettedContainer<>(biomeIds, biome, PalettedContainer.Strategy.SECTION_BIOMES));
    }

    private final class RefusingCells implements CellAccess {
        private static final long NONE = Long.MIN_VALUE;

        private final Set<Long> opened = ConcurrentHashMap.newKeySet();
        private volatile long refused = NONE;

        @Override
        public CellHandle open(long key) {
            if (key == refused) {
                throw new IllegalStateException("The fake cache refuses cell " + key + ".");
            }

            opened.add(key);
            return cells.open(key);
        }

        @Override
        public void release(CellHandle handle) {
            cells.release(handle);
        }
    }

    private final class Announcements implements CellChangeListener {
        @Override
        public void changed(CellHandle handle, int faceMask, int edgeMask, int occupancy, long sequence) {
            cells.release(handle);
        }

        @Override
        public void covered(int chunkX, int chunkZ) {
            announced.incrementAndGet();
        }
    }
}
