package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicReference;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.FaceMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.StateTable;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellCache;
import com.eminus.model.ModelMetadata;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkerHarness;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class MeshServiceTest {
    private static final int WORKER_THREADS = 2;
    private static final String MESH_SERVICE = "mesh";
    private static final int OPENED_CELLS = QuadGroups.FACE_COUNT + 1;

    private static final int STONE = 1;
    private static final int STONE_MODEL = 7;
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
            harness.register(MESH_SERVICE, MeshScratch::new), cache, models, opacity, delivered::set);

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
        assertEquals(QuadGroups.FACE_COUNT, delivered.get().quadCount());
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
    void eachNeighbourKeyStepsOneCellAlongItsOwnAxis() {
        assertEquals(CellKey.pack(LEVEL, 1, 2, 2), MeshService.neighbourKey(KEY, Direction.NORTH));
        assertEquals(CellKey.pack(LEVEL, 1, 2, 4), MeshService.neighbourKey(KEY, Direction.SOUTH));
        assertEquals(CellKey.pack(LEVEL, 0, 2, 3), MeshService.neighbourKey(KEY, Direction.WEST));
        assertEquals(CellKey.pack(LEVEL, 2, 2, 3), MeshService.neighbourKey(KEY, Direction.EAST));
        assertEquals(CellKey.pack(LEVEL, 1, 1, 3), MeshService.neighbourKey(KEY, Direction.DOWN));
        assertEquals(CellKey.pack(LEVEL, 1, 3, 3), MeshService.neighbourKey(KEY, Direction.UP));
    }

    private void seedCube() {
        Cell cell = Cell.blank(KEY);
        cell.set(8, 8, 8, VoxelEntry.pack(STONE, BIOME, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0)));
        store.write(cell);
    }
}
