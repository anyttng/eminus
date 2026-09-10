package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
    private static final long OUTSIDE = CellKey.pack(DetailLevel.MIN, 1, 2, 3);
    private static final int RING_COLUMNS = 5;
    private static final int ONE_CELL = 1;
    private static final double EYE_X = CELL_X * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL + 256.0;
    private static final double EYE_Y = 100.0;
    private static final double EYE_Z = CELL_Z * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL + 256.0;
    private static final double ONE_BLOCK = 1.0;
    private static final double TELEPORT = 2_000.0;
    private static final int ONE_OCTANT = 0b1;

    private final FakeCellStore store = new FakeCellStore();
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final CellCache cache = new CellCache(store, handle -> { }, () -> 0L);
    private final FakeBuilds builds = new FakeBuilds();
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
        manager.meshed(CellMesh.empty(KEY));

        FakeBuilds.Call rebuilt = builds.take();
        assertEquals(KEY, rebuilt.key());
        assertSame(handle, rebuilt.handle());
        assertEquals(2, rebuilt.references());
        assertTrue(builds.idle());
    }

    @Test
    void aFinishedMeshIsTakenAsOneBatchAndOnlyOnce() {
        startRing();

        CellMesh mesh = CellMesh.empty(KEY);
        manager.meshed(mesh);

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
        manager.meshed(first);
        manager.meshed(second);
        List<CellMesh> listed =
                awaitRenderList(frame(EYE_X, EYE_Z), list -> list.meshes().contains(second), meshes -> { }).meshes();
        assertEquals(1, listed.size());
        assertSame(second, listed.get(0));
    }

    @Test
    void aChildIsUploadedNoLaterThanTheListThatDrawsIt() {
        startRing();
        CellMesh parent = TestMeshes.of(KEY, ONE_OCTANT);
        manager.meshed(parent);
        manager.frame(close(EYE_X, EYE_Z));

        FakeBuilds.Call request = builds.take();
        assertEquals(CellKey.child(KEY, 0), request.key());
        CellMesh child = TestMeshes.of(request.key(), OccupancyMask.EMPTY);
        manager.meshed(child);
        manager.frame(close(EYE_X + ONE_BLOCK, EYE_Z));

        Set<CellMesh> uploaded = new HashSet<>();
        RenderList drawing = awaitRenderList(close(EYE_X + ONE_BLOCK, EYE_Z),
                list -> list.meshes().contains(child), uploaded::addAll);
        assertTrue(uploaded.contains(child));
        assertFalse(drawing.meshes().contains(parent));
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

        manager.meshed(CellMesh.empty(KEY));
        manager.frame(frame(EYE_X + ONE_BLOCK, EYE_Z));
        awaitWalks(3);
    }

    @Test
    void aRemovedColumnEvictsTheGeometryOfItsRoot() {
        startRing();
        CellMesh mesh = TestMeshes.of(KEY, OccupancyMask.EMPTY);
        manager.meshed(mesh);
        awaitBatch(taken -> taken.meshes().contains(mesh));

        manager.frame(frame(EYE_X + TELEPORT, EYE_Z));

        awaitBatch(taken -> taken.evicted().contains(KEY));
    }

    private void startRing() {
        manager.frame(frame(EYE_X, EYE_Z));

        Set<Long> roots = new HashSet<>();
        for (int column = 0; column < RING_COLUMNS; column++) {
            FakeBuilds.Call call = builds.take();
            assertNull(call.handle());
            roots.add(call.key());
        }

        assertTrue(roots.contains(KEY));
        assertTrue(roots.contains(WEST));
        assertTrue(builds.idle());
    }

    private void settleRing() {
        startRing();
        manager.meshed(CellMesh.empty(KEY));
        manager.meshed(CellMesh.empty(WEST));
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

    private TreeBatch awaitBatch(Predicate<TreeBatch> ready) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);

        while (System.nanoTime() < deadline) {
            TreeBatch batch = manager.batches().take();
            if (batch != null && ready.test(batch)) {
                return batch;
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
