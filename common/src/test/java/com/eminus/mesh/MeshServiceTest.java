package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.OccupancyMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.StateTable;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellCache;
import com.eminus.cell.cache.CellHandle;
import com.eminus.model.ModelMetadata;
import com.eminus.store.FakeCellStore;
import com.eminus.work.ShutdownMode;
import com.eminus.work.WorkService;
import com.eminus.work.WorkerHarness;
import com.eminus.work.WorkerPool;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class MeshServiceTest {
    private static final int WORKER_THREADS = 2;
    private static final int NO_WORKERS = 0;
    private static final long AWAIT_MILLIS = 10_000L;
    private static final int CUBE_AT = 8;
    private static final String MESH_SERVICE = "mesh";
    private static final String QUEUED_SERVICE = "queued";
    private static final int OPENED_CELLS = QuadGroups.DIRECTIONAL_COUNT + 1;
    private static final int FIRST_VOXEL = 0;
    private static final int LAST_VOXEL = DetailLevel.VOXELS_PER_SIDE - 1;
    private static final int NO_BLEND = 0;

    private static final int STONE = 1;
    private static final int STONE_MODEL = 7;
    private static final int GLASS = 2;
    private static final int GLASS_MODEL = 8;
    private static final int UNBAKED_BELOW = 3;
    private static final int UNBAKED_ABOVE = 4;
    private static final int BIOME = 2;
    private static final int LEVEL = 0;
    private static final long KEY = CellKey.pack(LEVEL, 1, 2, 3);

    private final FakeCellStore store = new FakeCellStore();
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final CellCache cache = new CellCache(store, handle -> { }, () -> 0L);
    private final FakeModels models = new FakeModels();
    private final AtomicReference<CellMesh> delivered = new AtomicReference<>();
    private final StateOpacity opacity = stateId -> stateId == STONE ? StateTable.FULL_OPACITY : 0;

    private final MeshService service = new MeshService(
            harness.register(MESH_SERVICE, MeshScratch::new), cache, ColumnCoverage.everything(), models,
            new FakeTints(), NO_BLEND, level -> opacity, (mesh, request) -> delivered.set(mesh));

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @AfterEach
    void stopTheHarness() {
        harness.close();
    }

    @Test
    void aFinishedMeshReachesTheListenerAndLeavesNoCellHeld() {
        models.define(STONE, STONE_MODEL,
                ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0));
        seedCube();

        harness.run(() -> service.build(MeshTask.fresh(KEY), new MeshScratch()));

        assertNotNull(delivered.get());
        assertEquals(QuadGroups.DIRECTIONAL_COUNT, delivered.get().quadCount());
        assertEquals(0, cache.liveCount());
        assertEquals(OPENED_CELLS, cache.parkedCount());
    }

    @Test
    void aMeshThatThrowsStillLeavesNoCellHeld() {
        models.define(STONE, STONE_MODEL,
                ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0));
        models.throwOnMetadata();
        seedCube();

        assertThrows(IllegalStateException.class,
                () -> harness.run(() -> service.build(MeshTask.fresh(KEY), new MeshScratch())));

        assertEquals(0, cache.liveCount());
        assertEquals(OPENED_CELLS, cache.parkedCount());
    }

    @Test
    void aCarriedReferenceIsReleasedByTheBuildTask() {
        models.define(STONE, STONE_MODEL,
                ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0));
        seedCube();

        harness.run(() -> {
            CellHandle handle = cache.open(KEY);
            service.build(MeshTask.carrying(KEY, handle, 1, MeshTask.NO_REQUEST, MeshTask.LOWEST_PRIORITY), new MeshScratch());
        });

        assertNotNull(delivered.get());
        assertEquals(0, cache.liveCount());
        assertEquals(OPENED_CELLS, cache.parkedCount());
    }

    @Test
    void everyCarriedReferenceIsReleasedByTheBuildTask() {
        models.define(STONE, STONE_MODEL,
                ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0));
        seedCube();

        harness.run(() -> {
            CellHandle handle = cache.open(KEY);
            cache.open(KEY);
            cache.open(KEY);
            service.build(MeshTask.carrying(KEY, handle, 3, MeshTask.NO_REQUEST, MeshTask.LOWEST_PRIORITY), new MeshScratch());
        });

        assertEquals(0, cache.liveCount());
        assertEquals(OPENED_CELLS, cache.parkedCount());
    }

    @Test
    void theMeshCarriesTheOccupancyOfItsCell() {
        models.define(STONE, STONE_MODEL,
                ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0));
        seedCube();

        harness.run(() -> service.build(MeshTask.fresh(KEY), new MeshScratch()));

        assertEquals(OccupancyMask.set(OccupancyMask.EMPTY, OccupancyMask.octantOf(CUBE_AT, CUBE_AT, CUBE_AT)),
                delivered.get().occupancy());
    }

    @Test
    void oneFailedAttemptQueuesOneRetryHoweverManyStatesItWaitsOn() {
        models.define(GLASS, GLASS_MODEL,
                ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.ALL, 0, 0));
        models.unbake(UNBAKED_BELOW);
        models.unbake(UNBAKED_ABOVE);
        seedGlassBetweenUnbakedStates();

        WorkerPool idle = WorkerPool.start(NO_WORKERS);
        WorkService<MeshScratch> queued = idle.register(QUEUED_SERVICE, 1, WorkService.UNLIMITED, MeshScratch::new);
        MeshService waiting = new MeshService(queued, cache, ColumnCoverage.everything(), models,
                new FakeTints(), NO_BLEND, level -> opacity, (mesh, request) -> delivered.set(mesh));

        try {
            harness.run(() -> waiting.build(MeshTask.fresh(KEY), new MeshScratch()));

            assertNull(delivered.get());
            assertEquals(2, models.waiting());

            models.publishBakes();

            assertEquals(1, waiting.backlog());
        } finally {
            queued.stop(ShutdownMode.DISCARD);
            idle.shutdown();
        }
    }

    @Test
    void aReleaseJobDropsEveryCarriedReference() {
        CellHandle handle = harness.call(() -> {
            CellHandle opened = cache.open(KEY);
            cache.open(KEY);
            return opened;
        });

        service.release(handle, 2);

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);
        while (cache.liveCount() > 0) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("The release job did not run within " + AWAIT_MILLIS + " ms.");
            }

            Thread.onSpinWait();
        }

        assertEquals(1, cache.parkedCount());
    }

    private void seedCube() {
        Cell cell = Cell.blank(KEY);
        cell.set(CUBE_AT, CUBE_AT, CUBE_AT, VoxelEntry.pack(STONE, BIOME, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0)));
        store.write(cell);
    }

    private void seedGlassBetweenUnbakedStates() {
        Cell cell = Cell.blank(KEY);
        cell.set(FIRST_VOXEL, CUBE_AT, CUBE_AT, lit(GLASS));
        cell.set(FIRST_VOXEL + 1, CUBE_AT, CUBE_AT, lit(UNBAKED_ABOVE));
        store.write(cell);

        Cell west = Cell.blank(CellKey.neighbour(KEY, Direction.WEST));
        west.set(LAST_VOXEL, CUBE_AT, CUBE_AT, lit(UNBAKED_BELOW));
        store.write(west);
    }

    private static long lit(int state) {
        return VoxelEntry.pack(state, BIOME, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0));
    }
}
