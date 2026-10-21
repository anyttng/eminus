package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.EdgeMask;
import com.eminus.cell.FaceMask;
import com.eminus.cell.OccupancyMask;
import com.eminus.cell.cache.CellCache;
import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshSummary;
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
    private static final long WEST = CellKey.neighbor(KEY, Direction.WEST);
    private static final long ABOVE = CellKey.neighbor(KEY, Direction.UP);
    private static final long BELOW = CellKey.neighbor(KEY, Direction.DOWN);
    private static final long OUTSIDE = CellKey.pack(DetailLevel.MIN, 1, 2, 3);
    private static final int RING_COLUMNS = 5;
    private static final int ONE_CELL = 1;
    private static final double EYE_X = CELL_X * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL + 256.0;
    private static final double EYE_Y = 100.0;
    private static final double EYE_Z = CELL_Z * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL + 256.0;
    private static final double ONE_BLOCK = 1.0;
    private static final double TELEPORT = 2_000.0;
    private static final int ONE_OCTANT = 0b1;
    private static final int ALL_OCTANTS = 0xFF;
    private static final int LOWEST_BELOW_TOP = DetailLevel.MAX - 1;
    private static final int OLDER_QUADS = 2;
    private static final int NEWER_QUADS = 3;
    private static final int QUEUED_ROOTS = 800;
    private static final List<long[]> NO_ROWS = List.of();
    private static final int COLLECTION_ATTEMPTS = 20;
    private static final long COLLECTION_PAUSE_MILLIS = 25L;
    private static final int BOUNDARY_CHUNK_X = CELL_X * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL / FarDistance.BLOCKS_PER_CHUNK;
    private static final int INTERIOR_CHUNK_Z = (int) EYE_Z / FarDistance.BLOCKS_PER_CHUNK;

    private final FakeCellStore store = new FakeCellStore();
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final CellCache cache = new CellCache(store, handle -> { }, () -> 0L);
    private final FakeBuilds builds = new FakeBuilds();
    private final Map<Long, Long> rootRequests = new HashMap<>();
    private final Map<Long, Float> rootPriorities = new HashMap<>();
    private TreeManager manager =
            TreeManager.start(builds, new TreeExtent(new CellFrame(0), 1, DetailLevel.MIN));
    private long sequence;

    @AfterEach
    void stopEverything() {
        manager.stop();
        harness.close();
    }

    @Test
    void aSecondChangeBeforeTheBuildLandsQueuesNoSecondBuild() {
        settleRing();

        CellHandle handle = open(KEY);
        change(handle, FaceMask.NONE, EdgeMask.NONE);

        FakeBuilds.Call first = builds.take();
        assertEquals(KEY, first.key());
        assertSame(handle, first.handle());
        assertEquals(1, first.references());

        change(open(KEY), FaceMask.NONE, EdgeMask.NONE);
        change(open(KEY), FaceMask.NONE, EdgeMask.NONE);
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

        change(open(KEY), FaceMask.NONE, EdgeMask.NONE);
        FakeBuilds.Call first = builds.take();
        change(open(KEY), FaceMask.NONE, EdgeMask.NONE);
        manager.meshed(CellMesh.empty(KEY), first.request());
        FakeBuilds.Call rebuilt = builds.take();

        CellMesh late = TestMeshes.of(KEY, OccupancyMask.EMPTY, OLDER_QUADS);
        CellMesh newest = TestMeshes.of(KEY, OccupancyMask.EMPTY, NEWER_QUADS);
        manager.meshed(late, first.request());
        manager.meshed(newest, rebuilt.request());

        Set<CellMesh> uploaded = new HashSet<>();
        RenderList drawing = awaitRenderList(frame(EYE_X + ONE_BLOCK, EYE_Z),
                list -> list.meshes().contains(newest.summary()), uploaded::addAll);
        assertFalse(uploaded.contains(late));
        assertFalse(drawing.meshes().contains(late.summary()));
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

        CellMesh first = TestMeshes.of(KEY, OccupancyMask.EMPTY, OLDER_QUADS);
        CellMesh second = TestMeshes.of(KEY, OccupancyMask.EMPTY, NEWER_QUADS);
        manager.meshed(first, rootRequests.get(KEY));
        manager.meshed(second, rootRequests.get(KEY));
        List<MeshSummary> listed = awaitRenderList(frame(EYE_X, EYE_Z),
                list -> list.meshes().contains(second.summary()), meshes -> { }).meshes();
        assertEquals(1, listed.size());
        assertEquals(second.summary(), listed.get(0));
    }

    @Test
    void aChildIsUploadedNoLaterThanTheListThatDrawsIt() {
        restartOverLeafChildren();
        startRing();
        CellMesh parent = TestMeshes.of(KEY, ONE_OCTANT);
        manager.meshed(parent, rootRequests.get(KEY));
        manager.frame(close(EYE_X, EYE_Z));

        FakeBuilds.Call request = builds.take();
        assertEquals(CellKey.child(KEY, 0), request.key());
        CellMesh child = TestMeshes.of(request.key(), ONE_OCTANT);
        manager.meshed(child, request.request());
        manager.frame(close(EYE_X + ONE_BLOCK, EYE_Z));

        Set<CellMesh> uploaded = new HashSet<>();
        RenderList drawing = awaitRenderList(close(EYE_X + ONE_BLOCK, EYE_Z),
                list -> list.meshes().contains(child.summary()), uploaded::addAll);
        assertTrue(uploaded.contains(child));
        assertFalse(drawing.meshes().contains(parent.summary()));
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
    void aBoundaryChangeRebuildsOnlyTheNeighborsThatAreNodes() {
        settleRing();

        CellHandle handle = open(KEY);
        change(handle, FaceMask.WEST | FaceMask.UP, EdgeMask.NONE);

        FakeBuilds.Call own = builds.take();
        assertEquals(KEY, own.key());
        assertSame(handle, own.handle());

        FakeBuilds.Call neighbor = builds.take();
        assertEquals(WEST, neighbor.key());
        assertNull(neighbor.handle());
        assertEquals(0, neighbor.references());
        assertTrue(builds.idle());
    }

    @Test
    void anEdgeChangeAboveTheSlopingLevelRebuildsNoCellAcrossTheEdge() {
        settleRing();

        change(open(KEY), FaceMask.NONE, EdgeMask.bit(-1, 0, 0) | EdgeMask.bit(-1, 0, -1));
        change(open(WEST), FaceMask.NONE, EdgeMask.NONE);

        assertEquals(KEY, builds.take().key());
        assertEquals(WEST, builds.take().key());
        assertTrue(builds.idle());
    }

    @Test
    void aBoundaryChangeOnACellWithoutANodeStillRebuildsTheNeighborNode() {
        settleRing();

        CellHandle handle = open(ABOVE);
        change(handle, FaceMask.DOWN, EdgeMask.NONE);

        FakeBuilds.Release released = builds.takeRelease();
        assertSame(handle, released.handle());

        FakeBuilds.Call neighbor = builds.take();
        assertEquals(KEY, neighbor.key());
        assertNull(neighbor.handle());
        assertTrue(builds.idle());
    }

    @Test
    void aChangeInsideTheCellBelowRebuildsTheNodeWhoseMeshReadItWhole() {
        settleRingReadingBelow(true);

        CellHandle handle = open(BELOW);
        change(handle, FaceMask.NONE, EdgeMask.NONE);

        assertSame(handle, builds.takeRelease().handle());
        FakeBuilds.Call above = builds.take();
        assertEquals(KEY, above.key());
        assertNull(above.handle());
        assertTrue(builds.idle());
    }

    @Test
    void aChangeInsideTheCellBelowLeavesANodeThatNeverReadItAlone() {
        settleRingReadingBelow(false);

        CellHandle handle = open(BELOW);
        change(handle, FaceMask.NONE, EdgeMask.NONE);

        assertSame(handle, builds.takeRelease().handle());
        syncMessages();
        assertTrue(builds.idle());
    }

    @Test
    void aNewlyCoveredChunkRebuildsTheNodesOnBothSidesOfTheBoundaryItTouches() {
        settleRing();

        manager.covered(BOUNDARY_CHUNK_X, INTERIOR_CHUNK_Z);

        long first = builds.take().key();
        long second = builds.take().key();
        assertEquals(Set.of(KEY, WEST), Set.of(first, second));
        assertTrue(builds.idle());
    }

    @Test
    void aChangeOnACellWithoutANodeReleasesItsHandle() {
        CellHandle handle = open(OUTSIDE);
        change(handle, FaceMask.NONE, EdgeMask.NONE);

        FakeBuilds.Release released = builds.takeRelease();
        assertSame(handle, released.handle());
        assertEquals(1, released.references());
        assertTrue(builds.idle());
    }

    @Test
    void aStillCameraWalksOnceAfterMotionThenSkipsAndAMovedCameraOrAChangedTreeWalks() {
        startRing();
        awaitWalks(1);

        manager.frame(frame(EYE_X, EYE_Z));
        awaitWalks(2);

        manager.frame(frame(EYE_X, EYE_Z));
        syncMessages();
        assertEquals(2, manager.walks());

        manager.frame(frame(EYE_X + ONE_BLOCK, EYE_Z));
        awaitWalks(3);

        manager.meshed(CellMesh.empty(KEY), rootRequests.get(KEY));
        manager.frame(frame(EYE_X + ONE_BLOCK, EYE_Z));
        awaitWalks(4);
    }

    @Test
    void aStillCameraWalksAfterMotionWhileOutOfViewCandidatesRemainAndSkipsOnceNoneDo() {
        startRing();
        manager.meshed(CellMesh.empty(KEY), rootRequests.get(KEY));
        manager.meshed(TestMeshes.of(WEST, ONE_OCTANT), rootRequests.get(WEST));
        manager.frame(east(EYE_X + ONE_BLOCK));
        awaitWalks(2);
        assertTrue(builds.idle());

        manager.frame(east(EYE_X + ONE_BLOCK));
        FakeBuilds.Call request = builds.take();
        assertEquals(CellKey.child(WEST, 0), request.key());
        awaitWalks(3);

        manager.meshed(CellMesh.empty(request.key()), request.request());
        manager.frame(east(EYE_X + ONE_BLOCK));
        awaitWalks(4);
        assertTrue(builds.idle());

        manager.frame(east(EYE_X + ONE_BLOCK));
        syncMessages();
        assertEquals(4, manager.walks());
    }

    @Test
    void outOfViewBuildsInFlightLeaveTheInViewBudgetWholeAndGoOutBelowIt() {
        startRing();
        manager.meshed(CellMesh.empty(KEY), rootRequests.get(KEY));
        manager.meshed(TestMeshes.of(WEST, ALL_OCTANTS), rootRequests.get(WEST));
        manager.frame(east(EYE_X + ONE_BLOCK));
        awaitWalks(2);

        manager.frame(east(EYE_X + ONE_BLOCK));
        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            FakeBuilds.Call child = builds.take();
            manager.meshed(TestMeshes.of(child.key(), ALL_OCTANTS), child.request());
        }

        manager.frame(east(EYE_X + ONE_BLOCK));
        for (int index = 0; index < RequestBudget.MAX_PER_WALK; index++) {
            assertTrue(builds.take().priority() < 0.0F);
        }

        manager.meshed(TestMeshes.of(KEY, ALL_OCTANTS), rootRequests.get(KEY));
        manager.frame(east(EYE_X + ONE_BLOCK));
        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            FakeBuilds.Call inView = builds.take();
            assertEquals(KEY, CellKey.parent(inView.key()));
            assertEquals(ProjectedSize.CONTAINS_CAMERA, inView.priority());
        }

        syncMessages();
        assertTrue(builds.idle());
    }

    @Test
    void afterAWalkUnderPressureTheOutOfViewPassRequestsOnAStillCamera() {
        startRing();
        manager.meshed(CellMesh.empty(KEY), rootRequests.get(KEY));
        manager.meshed(TestMeshes.of(WEST, ONE_OCTANT), rootRequests.get(WEST));
        manager.frame(FakeCameras.underPressure(east(EYE_X + ONE_BLOCK)));
        awaitWalks(2);
        assertTrue(builds.idle());

        manager.frame(east(EYE_X + ONE_BLOCK));
        assertEquals(CellKey.child(WEST, 0), builds.take().key());
    }

    @Test
    void underArenaPressureASetInViewIsEvictedAndNotRequestedAgain() throws Exception {
        restartOverLeafChildren();
        startRing();
        manager.meshed(TestMeshes.of(KEY, ONE_OCTANT), rootRequests.get(KEY));
        manager.frame(close(EYE_X, EYE_Z));
        FakeBuilds.Call request = builds.take();
        CellMesh child = TestMeshes.of(request.key(), ONE_OCTANT);
        manager.meshed(child, request.request());
        awaitRenderList(close(EYE_X + ONE_BLOCK, EYE_Z), list -> list.meshes().contains(child.summary()),
                meshes -> { });

        manager.frame(FakeCameras.underPressure(close(EYE_X + 2 * ONE_BLOCK, EYE_Z)));
        manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        manager.frame(close(EYE_X + 2 * ONE_BLOCK, EYE_Z));
        TreeState state = manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        Map<String, Long> churn = manager.churn().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);

        assertEquals(1L, state.pressureEvictions());
        assertTrue(builds.idle());
        assertEquals(0L, churn.get(TreeChurn.REQUESTED_AGAIN));
        assertEquals(1L, churn.get(TreeChurn.EVICTED_FAR));
        assertEquals(0L, churn.get(TreeChurn.HORIZON));
    }

    @Test
    void aSetEvictedUnderPressureLeavesTheHorizonBoundedOnceThePressureLifts() throws Exception {
        restartOverLeafChildren();
        startRing();
        manager.meshed(TestMeshes.of(KEY, ONE_OCTANT), rootRequests.get(KEY));
        manager.frame(close(EYE_X, EYE_Z));
        FakeBuilds.Call request = builds.take();
        CellMesh child = TestMeshes.of(request.key(), ONE_OCTANT);
        manager.meshed(child, request.request());
        awaitRenderList(close(EYE_X + ONE_BLOCK, EYE_Z), list -> list.meshes().contains(child.summary()),
                meshes -> { });
        manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        assertFalse(manager.horizonBounded());

        manager.frame(FakeCameras.underPressure(close(EYE_X + 2 * ONE_BLOCK, EYE_Z)));
        manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        manager.frame(close(EYE_X + 2 * ONE_BLOCK, EYE_Z));
        TreeState state = manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);

        assertEquals(1L, state.pressureEvictions());
        assertTrue(manager.horizonBounded());
    }

    @Test
    void anArenaPressureLiftedOnAStillCameraStartsAWalkThatRequests() {
        startRing();
        manager.meshed(TestMeshes.of(KEY, ONE_OCTANT), rootRequests.get(KEY));
        manager.frame(FakeCameras.underPressure(close(EYE_X, EYE_Z)));
        awaitWalks(2);
        assertTrue(builds.idle());

        manager.frame(close(EYE_X, EYE_Z));
        assertEquals(KEY, CellKey.parent(builds.take().key()));
        awaitWalks(3);
    }

    @Test
    void aTableFilledOutOfViewGivesWayToTheNodesInView() throws Exception {
        manager.stop();
        manager = TreeManager.start(builds, new TreeExtent(new CellFrame(0), 1, DetailLevel.MIN),
                RING_COLUMNS + OccupancyMask.OCTANTS);
        startRing();
        manager.meshed(CellMesh.empty(KEY), rootRequests.get(KEY));
        manager.meshed(TestMeshes.of(WEST, ALL_OCTANTS), rootRequests.get(WEST));
        manager.frame(east(EYE_X + ONE_BLOCK));
        awaitWalks(2);

        manager.frame(east(EYE_X + ONE_BLOCK));
        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            FakeBuilds.Call behind = builds.take();
            assertEquals(WEST, CellKey.parent(behind.key()));
            manager.meshed(TestMeshes.of(behind.key(), OccupancyMask.EMPTY), behind.request());
        }

        manager.meshed(TestMeshes.of(KEY, ALL_OCTANTS), rootRequests.get(KEY));
        manager.frame(east(EYE_X + ONE_BLOCK));
        syncMessages();
        manager.frame(east(EYE_X + ONE_BLOCK));

        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            assertEquals(KEY, CellKey.parent(builds.take().key()));
        }

        TreeState state = manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(OccupancyMask.OCTANTS, state.pressureEvictions());
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

    @Test
    void aMeshIsUnreachableOnceItsBatchIsTaken() {
        startRing();

        assertCleared(meshOneRoot());
    }

    // Inlined into the test, the mesh would stay on that frame's stack and the assertion would read it rather than the tree.
    private WeakReference<CellMesh> meshOneRoot() {
        CellMesh mesh = TestMeshes.of(KEY, OccupancyMask.EMPTY);
        WeakReference<CellMesh> reference = new WeakReference<>(mesh);
        manager.meshed(mesh, rootRequests.get(KEY));
        awaitBatch(taken -> taken.meshes().contains(mesh));

        return reference;
    }

    private static void assertCleared(WeakReference<CellMesh> mesh) {
        for (int attempt = 0; attempt < COLLECTION_ATTEMPTS; attempt++) {
            System.gc();
            if (mesh.refersTo(null)) {
                return;
            }

            try {
                Thread.sleep(COLLECTION_PAUSE_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        fail("The mesh is still reachable after its batch was taken, so something holds its quads and colors.");
    }

    private void restartOverLeafChildren() {
        manager.stop();
        manager = TreeManager.start(builds, new TreeExtent(new CellFrame(0), 1, LOWEST_BELOW_TOP));
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
        change(open(OUTSIDE), FaceMask.NONE, EdgeMask.NONE);
        builds.takeRelease();
    }

    private void settleRingReadingBelow(boolean readsBelow) {
        startRing();
        manager.meshed(CellMesh.empty(KEY).readingBelow(readsBelow), rootRequests.get(KEY));
        manager.meshed(CellMesh.empty(WEST), rootRequests.get(WEST));
        syncMessages();
    }

    private static CameraFrame frame(double x, double z) {
        return FakeCameras.everything(x, EYE_Y, z, ONE_CELL, FakeCameras.FAR_PIXELS_PER_BLOCK);
    }

    private static CameraFrame close(double x, double z) {
        return FakeCameras.everything(x, EYE_Y, z, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private static CameraFrame east(double x) {
        return FakeCameras.looking(x, EYE_Y, EYE_Z, 1.0F, 0.0F, 0.0F, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private void syncMessages() {
        change(open(OUTSIDE), FaceMask.NONE, EdgeMask.NONE);
        builds.takeRelease();
    }

    private void change(CellHandle handle, int faceMask, int edgeMask) {
        manager.changed(handle, faceMask, edgeMask, OccupancyMask.EMPTY, ++sequence);
    }

    private CellHandle open(long key) {
        return harness.call(() -> cache.open(key));
    }

    private void awaitWalks(long walks) {
        await(() -> manager.walks() >= walks, "The tree did not reach walk " + walks);
        assertEquals(walks, manager.walks());
    }

    // A batch the tree built while the slot was full is offered only after its next message, and describing no rows changes nothing else.
    @SuppressWarnings("FutureReturnValueIgnored")
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
