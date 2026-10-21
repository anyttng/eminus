package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
import com.eminus.settings.FarDistance;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkerHarness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class TreeManagerStaleTest {
    private static final int WORKER_THREADS = 1;
    private static final long AWAIT_MILLIS = 10_000L;
    private static final int RING_COLUMNS = 5;
    private static final int ONE_CELL = 1;
    private static final int CELL_X = 1;
    private static final int CELL_Z = 3;
    private static final long KEY = CellKey.pack(DetailLevel.MAX, CELL_X, 0, CELL_Z);
    private static final long CHILD = CellKey.child(KEY, 0);
    private static final long FILLED_CHILD = CellKey.child(KEY, 1);
    private static final long OUTSIDE = CellKey.pack(DetailLevel.MIN, 1, 2, 3);
    private static final double EYE_X = CELL_X * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL + 256.0;
    private static final double EYE_Y = 100.0;
    private static final double ABOVE_THE_CELL_Y = 2.0 * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL;
    private static final double EYE_Z = CELL_Z * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL + 256.0;
    private static final double ONE_BLOCK = 1.0;
    private static final int FIRST_OCTANT = 0b1;
    private static final int FIRST_TWO_OCTANTS = 0b11;
    private static final int NEWER_QUADS = 3;

    private final FakeCellStore store = new FakeCellStore();
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final CellCache cache = new CellCache(store, handle -> { }, () -> 0L);
    private final FakeBuilds builds = new FakeBuilds();
    private final TreeManager manager =
            TreeManager.start(builds, new TreeExtent(new CellFrame(0), 1, DetailLevel.MAX - 1));
    private CellMesh child;
    private long sequence;

    @AfterEach
    void stopEverything() {
        manager.stop();
        harness.close();
    }

    @BeforeEach
    void giveWayToTheFirstChild() {
        manager.frame(close(EYE_X));
        Map<Long, Long> roots = new HashMap<>();
        for (int column = 0; column < RING_COLUMNS; column++) {
            FakeBuilds.Call call = builds.take();
            roots.put(call.key(), call.request());
        }

        manager.meshed(TestMeshes.of(KEY, FIRST_OCTANT), roots.get(KEY));
        manager.frame(close(EYE_X + ONE_BLOCK));
        FakeBuilds.Call request = builds.take();
        assertEquals(CHILD, request.key());
        child = TestMeshes.of(CHILD, FIRST_OCTANT);
        manager.meshed(child, request.request());
        awaitRenderList(close(EYE_X + 2 * ONE_BLOCK), list -> list.meshes().contains(child.summary()));
    }

    @Test
    void aChangeOnANodeThatGaveWayBuildsNothingAndReleasesItsCell() {
        CellHandle handle = open(KEY);
        change(handle, FIRST_OCTANT);

        assertSame(handle, builds.takeRelease().handle());
        syncMessages();
        assertTrue(builds.idle());
    }

    @Test
    void anOctantAChangeFillsUnderANodeThatGaveWayHasItsChildRequested() {
        change(open(KEY), FIRST_TWO_OCTANTS);
        builds.takeRelease();

        manager.frame(close(EYE_X + 2 * ONE_BLOCK));

        assertEquals(FILLED_CHILD, builds.take().key());
    }

    @Test
    void aStaleNodeDrawnAgainIsRebuiltWhileItsChildrenStandInForIt() {
        change(open(KEY), FIRST_OCTANT);
        builds.takeRelease();

        RenderList standingIn = awaitRenderList(farAbove(), list -> list.meshes().contains(child.summary()));
        FakeBuilds.Call rebuild = builds.take();
        assertEquals(KEY, rebuild.key());
        assertEquals(1, standingIn.meshes().size());

        CellMesh rebuilt = TestMeshes.of(KEY, FIRST_OCTANT, NEWER_QUADS);
        manager.meshed(rebuilt, rebuild.request());
        RenderList drawn = awaitRenderList(farAbove(), list -> list.meshes().contains(rebuilt.summary()));
        assertFalse(drawn.meshes().contains(child.summary()));
    }

    private void change(CellHandle handle, int occupancy) {
        manager.changed(handle, FaceMask.NONE, EdgeMask.NONE, occupancy, ++sequence);
    }

    private void syncMessages() {
        manager.changed(open(OUTSIDE), FaceMask.NONE, EdgeMask.NONE, OccupancyMask.EMPTY, ++sequence);
        builds.takeRelease();
    }

    private CellHandle open(long key) {
        return harness.call(() -> cache.open(key));
    }

    private static CameraFrame close(double x) {
        return FakeCameras.everything(x, EYE_Y, EYE_Z, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private static CameraFrame farAbove() {
        return FakeCameras.everything(EYE_X, ABOVE_THE_CELL_Y, EYE_Z, ONE_CELL, FakeCameras.FAR_PIXELS_PER_BLOCK);
    }

    private RenderList awaitRenderList(CameraFrame frame, Predicate<RenderList> ready) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);

        while (System.nanoTime() < deadline) {
            manager.frame(frame);
            TreeBatch batch = manager.batches().take();
            RenderList list = batch == null ? null : batch.renderList();
            if (list != null && ready.test(list)) {
                return list;
            }

            Thread.onSpinWait();
        }

        throw new IllegalStateException("The render list did not settle within " + AWAIT_MILLIS + " ms.");
    }
}
