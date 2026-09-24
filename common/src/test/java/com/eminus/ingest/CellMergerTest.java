package com.eminus.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.eminus.cell.CellFrame;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.EdgeMask;
import com.eminus.cell.FaceMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellAccess;
import com.eminus.cell.cache.CellCache;
import com.eminus.cell.cache.CellHandle;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkerHarness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class CellMergerTest {
    private static final int WORKER_THREADS = 1;
    private static final int LOWEST_STORED_LEVEL = 0;
    private static final int MIN_BLOCK_Y = -64;
    private static final int STONE = 1;
    private static final int INTERIOR = 5;
    private static final int STORED_BIOME = 7;
    private static final int SHADE = 7;
    private static final int COARSE_LOWEST_STORED_LEVEL = 1;

    private static final int[] OPACITY = {0, 15};
    private static final StateOpacity OPACITIES = state -> OPACITY[state];

    private final AtomicLong clock = new AtomicLong();
    private final FakeCellStore store = new FakeCellStore();
    private final List<CellHandle> saved = Collections.synchronizedList(new ArrayList<>());
    private final CountingCells cells = new CountingCells(new CellCache(store, saved::add, clock::get));
    private final List<Change> changes = Collections.synchronizedList(new ArrayList<>());
    private final CellFrame frame = new CellFrame(MIN_BLOCK_Y);
    private final CellMerger merger = new CellMerger(cells, frame, LOWEST_STORED_LEVEL, this::record);
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final SectionPyramid pyramid = new SectionPyramid();

    @AfterEach
    void stopTheHarness() {
        harness.close();
    }

    @Test
    void aFirstMergeReachesEveryLevelFromTheLowestStoredOne() {
        buildFrom(entry(STONE));

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertEquals(DetailLevel.COUNT, cells.opens());
        assertEquals(DetailLevel.COUNT, changes.size());
    }

    @Test
    void anUnchangedLevelStopsTheClimb() {
        buildFrom(entry(STONE));
        harness.run(() -> merger.merge(pyramid, 0, 0, 0));
        int opened = cells.opens();
        changes.clear();

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertEquals(1, cells.opens() - opened);
        assertTrue(changes.isEmpty());
    }

    @Test
    void aChangeOnTheCellBoundarySetsThatFacesBit() {
        buildFrom(VoxelEntry.AIR);
        set(0, 0, 0, entry(STONE));
        PyramidDownsampler.build(pyramid, OPACITIES);

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertEquals(FaceMask.DOWN | FaceMask.NORTH | FaceMask.WEST, changes.get(0).faceMask());
    }

    @Test
    void aChangeOnTheCellCornerReachesTheCellsAcrossItsEdges() {
        buildFrom(VoxelEntry.AIR);
        set(0, 0, 0, entry(STONE));
        PyramidDownsampler.build(pyramid, OPACITIES);

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertEquals(EdgeMask.bit(-1, 0, -1) | EdgeMask.bit(-1, -1, 0) | EdgeMask.bit(0, -1, -1)
                | EdgeMask.bit(-1, -1, -1), changes.get(0).edgeMask());
    }

    @Test
    void anInteriorChangeSetsNoFace() {
        buildFrom(VoxelEntry.AIR);
        set(INTERIOR, INTERIOR, INTERIOR, entry(STONE));
        PyramidDownsampler.build(pyramid, OPACITIES);

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertEquals(FaceMask.NONE, changes.get(0).faceMask());
        assertEquals(EdgeMask.NONE, changes.get(0).edgeMask());
    }

    @Test
    void theChangedCellArrivesDirtyAndNoLongerEmpty() {
        buildFrom(VoxelEntry.AIR);
        set(INTERIOR, INTERIOR, INTERIOR, entry(STONE));
        PyramidDownsampler.build(pyramid, OPACITIES);

        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        Change change = changes.get(0);
        assertTrue(change.dirty());
        assertFalse(change.empty());
    }

    @Test
    void aKeptBiomeTakesTheStoredOneOnEveryLevel() {
        buildFrom(VoxelEntry.pack(STONE, STORED_BIOME, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0)));
        harness.run(() -> merger.merge(pyramid, 0, 0, 0));
        changes.clear();

        buildFrom(VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, VoxelEntry.KEPT_BIOME,
                VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0)));
        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertEquals(DetailLevel.COUNT, changes.size());
        int[] biomes = new int[DetailLevel.COUNT];
        harness.run(() -> {
            for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
                CellHandle handle = cells.open(frame.keyAt(level, 0, 0, 0));
                int voxelX = frame.voxelX(0, level);
                int voxelY = frame.voxelY(0, level);
                int voxelZ = frame.voxelZ(0, level);
                biomes[level] = handle.withCell(cell -> VoxelEntry.biome(cell.get(voxelX, voxelY, voxelZ)));
                cells.release(handle);
            }
        });

        for (int biome : biomes) {
            assertEquals(STORED_BIOME, biome);
        }
    }

    @Test
    void aNeverWrittenSectionHoldsOnlyOpenSky() {
        assertFalse(probe(merger, 0, 0, 0));
    }

    @Test
    void aStoredBlockIsBeyondOpenSky() {
        buildFrom(VoxelEntry.AIR);
        set(INTERIOR, INTERIOR, INTERIOR, entry(STONE));
        PyramidDownsampler.build(pyramid, OPACITIES);
        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertTrue(probe(merger, 0, 0, 0));
    }

    @Test
    void storedShadeIsBeyondOpenSky() {
        buildFrom(VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, STORED_BIOME, VoxelEntry.light(SHADE, 0)));
        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertTrue(probe(merger, 0, 0, 0));
    }

    @Test
    void aStoredBiomeUnderFullSkyIsStillOpenSky() {
        buildFrom(VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, STORED_BIOME, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0)));
        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertFalse(probe(merger, 0, 0, 0));
    }

    @Test
    void aBlockInAnotherSectionOfTheSameCellLeavesThisOneOpenSky() {
        buildFrom(entry(STONE));
        harness.run(() -> merger.merge(pyramid, 0, 0, 0));

        assertFalse(probe(merger, 1, 0, 0));
    }

    @Test
    void aCoarserLowestStoredLevelReadsOnlyItsSectionsVoxels() {
        CellMerger coarse = new CellMerger(cells, frame, COARSE_LOWEST_STORED_LEVEL, this::record);
        buildFrom(entry(STONE));
        harness.run(() -> coarse.merge(pyramid, 0, 0, 0));

        assertTrue(probe(coarse, 0, 0, 0));
        assertFalse(probe(coarse, 1, 0, 0));
    }

    private boolean probe(CellMerger with, int sectionX, int sectionY, int sectionZ) {
        AtomicBoolean answer = new AtomicBoolean();
        harness.run(() -> answer.set(with.storesBeyondOpenSky(sectionX, sectionY, sectionZ)));
        return answer.get();
    }

    private void record(CellHandle handle, int faceMask, int edgeMask) {
        changes.add(new Change(faceMask, edgeMask, handle.dirty(), handle.withCell(cell -> cell.isEmpty())));
        cells.release(handle);
    }

    private void buildFrom(long entry) {
        Arrays.fill(pyramid.level(DetailLevel.MIN), entry);
        PyramidDownsampler.build(pyramid, OPACITIES);
    }

    private void set(int x, int y, int z, long entry) {
        pyramid.level(DetailLevel.MIN)[SectionPyramid.indexAt(SectionPyramid.SECTION_SIDE, x, y, z)] = entry;
    }

    private static long entry(int state) {
        return VoxelEntry.pack(state, 0, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0));
    }

    private record Change(int faceMask, int edgeMask, boolean dirty, boolean empty) {
    }

    private static final class CountingCells implements CellAccess {
        private final CellCache cache;
        private final AtomicInteger opens = new AtomicInteger();

        private CountingCells(CellCache cache) {
            this.cache = cache;
        }

        @Override
        public CellHandle open(long key) {
            opens.incrementAndGet();
            return cache.open(key);
        }

        @Override
        public void release(CellHandle handle) {
            cache.release(handle);
        }

        private int opens() {
            return opens.get();
        }
    }
}
