package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.eminus.api.v1.TreeState;
import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.OccupancyMask;
import com.eminus.cell.cache.CellCache;
import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.CellMesh;
import com.eminus.settings.FarDistance;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkerHarness;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class TreeManagerTest {
    private static final int WORKER_THREADS = 2;
    private static final long AWAIT_MILLIS = 10_000L;
    private static final int CELL_X = 1;
    private static final int CELL_Z = 3;
    private static final long KEY = CellKey.pack(DetailLevel.MAX, CELL_X, 0, CELL_Z);
    private static final long WEST = CellKey.neighbour(KEY, Direction.WEST);
    private static final long ABOVE = CellKey.neighbour(KEY, Direction.UP);
    private static final long OUTSIDE = CellKey.pack(DetailLevel.MIN, 1, 2, 3);
    private static final int RING_COLUMNS = 5;
    private static final int ONE_CELL = 1;
    private static final double EYE_X = CELL_X * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL + 256.0;
    private static final double EYE_Y = 100.0;
    private static final double EYE_Z = CELL_Z * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL + 256.0;
    private static final double ONE_BLOCK = 1.0;
    private static final double TELEPORT = 2_000.0;
    private static final int ONE_OCTANT = 0b1;
    private static final int QUEUED_ROOTS = 800;
    private static final List<long[]> NO_ROWS = List.of();
    private static final int BOUNDARY_CHUNK_X = CELL_X * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL / FarDistance.BLOCKS_PER_CHUNK;
    private static final int INTERIOR_CHUNK_Z = (int) EYE_Z / FarDistance.BLOCKS_PER_CHUNK;

    private final FakeCellStore store = new FakeCellStore();
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final CellCache cache = new CellCache(store, handle -> { }, () -> 0L);
    private final FakeBuilds builds = new FakeBuilds();
    private final Map<Long, Long> rootRequests = new HashMap<>();
    private final Map<Long, Float> rootPriorities = new HashMap<>();
    private final TreeManager manager =
            TreeManager.start(builds, new TreeExtent(new CellFrame(0), 1, DetailLevel.MIN));

    @AfterEach
    void stopEverything() {
        manager.stop();
        harness.close();
    }

    @Test
    void aSecondChangeBeforeTheBuildLandsQueuesNoSecondBuild() {
        settleRing();

        CellHandle handle = open(KEY);
        manager.changed(handle, FaceMask.NONE);

        FakeBuilds.Call first = builds.take();
        assertEquals(KEY, first.key());
        assertSame(handle, first.handle());
        assertEquals(1, first.references());

        manager.changed(open(KEY), FaceMask.NONE);
        manager.changed(open(KEY), FaceMask.NONE);
        manager.meshed(CellMesh.empty(KEY), first.request());

        FakeBuilds.Call rebuilt = builds.take();
        assertEquals(KEY, rebuilt.key());
        assertSame(handle, rebuilt.handle());
        assertEquals(2, rebuilt.references());
        assertTrue(builds.idle());
    }

    @Test
    void aMeshOfAnOlderRequestNeverReplacesTheNewerOne() {
        settleRing();

        manager.changed(open(KEY), FaceMask.NONE);
        FakeBuilds.Call first = builds.take();
        manager.changed(open(KEY), FaceMask.NONE);
        manager.meshed(CellMesh.empty(KEY), first.request());
        FakeBuilds.Call rebuilt = builds.take();

        CellMesh late = TestMeshes.of(KEY, OccupancyMask.EMPTY);
        CellMesh newest = TestMeshes.of(KEY, OccupancyMask.EMPTY);
        manager.meshed(late, first.request());
        manager.meshed(newest, rebuilt.request());

        Set<CellMesh> uploaded = new HashSet<>();
        RenderList drawing = awaitRenderList(frame(EYE_X + ONE_BLOCK, EYE_Z),
                list -> list.meshes().contains(newest), uploaded::addAll);
        assertFalse(uploaded.contains(late));
        assertFalse(drawing.meshes().contains(late));
    }

    @Test
    void aFinishedMeshIsTakenAsOneBatchAndOnlyOnce() {
        startRing();

        CellMesh mesh = CellMesh.empty(KEY);
        manager.meshed(mesh, rootRequests.get(KEY));

        TreeBatch batch = awaitBatch(taken -> !taken.meshes().isEmpty());
        assertEquals(List.of(mesh), batch.meshes());
        assertNull(manager.batches().take());
        assertTrue(builds.idle());
    }

    @Test
    void theRenderListCarriesEveryMeshedNodeOnceWithItsNewestMesh() {
        startRing();

        CellMesh first = TestMeshes.of(KEY, OccupancyMask.EMPTY);
        CellMesh second = TestMeshes.of(KEY, OccupancyMask.EMPTY);
        manager.meshed(first, rootRequests.get(KEY));
        manager.meshed(second, rootRequests.get(KEY));
        List<CellMesh> listed =
                awaitRenderList(frame(EYE_X, EYE_Z), list -> list.meshes().contains(second), meshes -> { }).meshes();
        assertEquals(1, listed.size());
        assertSame(second, listed.get(0));
    }

    @Test
    void aChildIsUploadedNoLaterThanTheListThatDrawsIt() {
        startRing();
        CellMesh parent = TestMeshes.of(KEY, ONE_OCTANT);
        manager.meshed(parent, rootRequests.get(KEY));
        manager.frame(close(EYE_X, EYE_Z));

        FakeBuilds.Call request = builds.take();
        assertEquals(CellKey.child(KEY, 0), request.key());
        CellMesh child = TestMeshes.of(request.key(), OccupancyMask.EMPTY);
        manager.meshed(child, request.request());
        manager.frame(close(EYE_X + ONE_BLOCK, EYE_Z));

        Set<CellMesh> uploaded = new HashSet<>();
        RenderList drawing = awaitRenderList(close(EYE_X + ONE_BLOCK, EYE_Z),
                list -> list.meshes().contains(child), uploaded::addAll);
        assertTrue(uploaded.contains(child));
        assertFalse(drawing.meshes().contains(parent));
    }

    @Test
    void aFullQueueOfRootBuildsStillLetsTheNodeUnderTheCameraRequestItsChildren() {
        startRing();
        builds.backlog(QUEUED_ROOTS);
        manager.meshed(TestMeshes.of(KEY, ONE_OCTANT), rootRequests.get(KEY));
        manager.frame(close(EYE_X, EYE_Z));

        FakeBuilds.Call request = builds.take();
        assertEquals(CellKey.child(KEY, 0), request.key());
        assertEquals(ProjectedSize.CONTAINS_CAMERA, request.priority());
    }

    @Test
    void aRootUnderTheCameraIsBuiltAheadOfTheRootsAroundIt() {
        startRing();

        assertEquals(ProjectedSize.CONTAINS_CAMERA, rootPriorities.get(KEY));
        assertTrue(rootPriorities.get(WEST) < rootPriorities.get(KEY));
    }

    @Test
    void aBoundaryChangeRebuildsOnlyTheNeighboursThatAreNodes() {
        settleRing();

        CellHandle handle = open(KEY);
        manager.changed(handle, FaceMask.WEST | FaceMask.UP);

        FakeBuilds.Call own = builds.take();
        assertEquals(KEY, own.key());
        assertSame(handle, own.handle());

        FakeBuilds.Call neighbour = builds.take();
        assertEquals(WEST, neighbour.key());
        assertNull(neighbour.handle());
        assertEquals(0, neighbour.references());
        assertTrue(builds.idle());
    }

    @Test
    void aBoundaryChangeOnACellWithoutANodeStillRebuildsTheNeighbourNode() {
        settleRing();

        CellHandle handle = open(ABOVE);
        manager.changed(handle, FaceMask.DOWN);

        FakeBuilds.Release released = builds.takeRelease();
        assertSame(handle, released.handle());

        FakeBuilds.Call neighbour = builds.take();
        assertEquals(KEY, neighbour.key());
        assertNull(neighbour.handle());
        assertTrue(builds.idle());
    }

    @Test
    void aNewlyCoveredChunkRebuildsTheNodesOnBothSidesOfTheBoundaryItTouches() {
        settleRing();

        manager.covered(BOUNDARY_CHUNK_X, INTERIOR_CHUNK_Z);

        Set<Long> rebuilt = Set.of(builds.take().key(), builds.take().key());
        assertEquals(Set.of(KEY, WEST), rebuilt);
        assertTrue(builds.idle());
    }

    @Test
    void aChangeOnACellWithoutANodeReleasesItsHandle() {
        CellHandle handle = open(OUTSIDE);
        manager.changed(handle, FaceMask.NONE);

        FakeBuilds.Release released = builds.takeRelease();
        assertSame(handle, released.handle());
        assertEquals(1, released.references());
        assertTrue(builds.idle());
    }

    @Test
    void aStillCameraSkipsTheWalkAndAMovedCameraOrAChangedTreeWalks() {
        startRing();
        awaitWalks(1);

        manager.frame(frame(EYE_X, EYE_Z));
        manager.changed(open(OUTSIDE), FaceMask.NONE);
        builds.takeRelease();
        assertEquals(1, manager.walks());

        manager.frame(frame(EYE_X + ONE_BLOCK, EYE_Z));
        awaitWalks(2);

        manager.meshed(CellMesh.empty(KEY), rootRequests.get(KEY));
        manager.frame(frame(EYE_X + ONE_BLOCK, EYE_Z));
        awaitWalks(3);
    }

    @Test
    void underArenaPressureATreeInViewEvictsNothingAndRequestsNothingAgain() throws Exception {
        startRing();
        manager.meshed(TestMeshes.of(KEY, ONE_OCTANT), rootRequests.get(KEY));
        manager.frame(close(EYE_X, EYE_Z));
        FakeBuilds.Call request = builds.take();
        CellMesh child = TestMeshes.of(request.key(), OccupancyMask.EMPTY);
        manager.meshed(child, request.request());
        awaitRenderList(close(EYE_X + ONE_BLOCK, EYE_Z), list -> list.meshes().contains(child), meshes -> { });

        CameraFrame pressed = FakeCameras.underPressure(close(EYE_X + 2 * ONE_BLOCK, EYE_Z));
        manager.frame(pressed);
        manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        manager.frame(pressed);
        TreeState state = manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);

        assertEquals(0L, state.pressureEvictions());
        assertTrue(builds.idle());
    }

    @Test
    void aRemovedColumnEvictsTheGeometryOfItsRoot() {
        startRing();
        CellMesh mesh = TestMeshes.of(KEY, OccupancyMask.EMPTY);
        manager.meshed(mesh, rootRequests.get(KEY));
        awaitBatch(taken -> taken.meshes().contains(mesh));

        manager.frame(frame(EYE_X + TELEPORT, EYE_Z));

        awaitBatch(taken -> taken.evicted().contains(KEY));
    }

    private void startRing() {
        manager.frame(frame(EYE_X, EYE_Z));

        for (int column = 0; column < RING_COLUMNS; column++) {
            FakeBuilds.Call call = builds.take();
            assertNull(call.handle());
            rootRequests.put(call.key(), call.request());
            rootPriorities.put(call.key(), call.priority());
        }

        assertTrue(rootRequests.containsKey(KEY));
        assertTrue(rootRequests.containsKey(WEST));
        assertTrue(builds.idle());
    }

    private void settleRing() {
        startRing();
        manager.meshed(CellMesh.empty(KEY), rootRequests.get(KEY));
        manager.meshed(CellMesh.empty(WEST), rootRequests.get(WEST));
        manager.changed(open(OUTSIDE), FaceMask.NONE);
        builds.takeRelease();
    }

    private static CameraFrame frame(double x, double z) {
        return FakeCameras.everything(x, EYE_Y, z, ONE_CELL, FakeCameras.FAR_PIXELS_PER_BLOCK);
    }

    private static CameraFrame close(double x, double z) {
        return FakeCameras.everything(x, EYE_Y, z, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private CellHandle open(long key) {
        return harness.call(() -> cache.open(key));
    }

    private void awaitWalks(long walks) {
        await(() -> manager.walks() >= walks, "The tree did not reach walk " + walks);
        assertEquals(walks, manager.walks());
    }

    // A batch the tree built while the slot was full is offered only after its next message, and describing no rows changes nothing else.
    private TreeBatch awaitBatch(Predicate<TreeBatch> ready) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);

        while (System.nanoTime() < deadline) {
            TreeBatch batch = manager.batches().take();
            if (batch != null) {
                if (ready.test(batch)) {
                    return batch;
                }

                manager.describe(NO_ROWS);
            }

            Thread.onSpinWait();
        }

        throw new IllegalStateException("No matching batch arrived within " + AWAIT_MILLIS + " ms.");
    }

    // Frames keep coming while the render thread waits, and each one lets the tree re-offer a held batch.
    private RenderList awaitRenderList(CameraFrame frame, Predicate<RenderList> ready,
            Consumer<List<CellMesh>> uploaded) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);

        while (System.nanoTime() < deadline) {
            manager.frame(frame);
            TreeBatch batch = manager.batches().take();
            if (batch != null) {
                uploaded.accept(batch.meshes());
                RenderList list = batch.renderList();
                if (list != null && ready.test(list)) {
                    return list;
                }
            }

            Thread.onSpinWait();
        }

        throw new IllegalStateException("The render list did not settle within " + AWAIT_MILLIS + " ms.");
    }

    private static void await(BooleanSupplier ready, String failure) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);

        while (!ready.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException(failure + " within " + AWAIT_MILLIS + " ms.");
            }

            Thread.onSpinWait();
        }
    }
}
