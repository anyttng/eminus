package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.EdgeMask;
import com.eminus.cell.FaceMask;
import com.eminus.cell.OccupancyMask;
import com.eminus.cell.cache.CellCache;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshSummary;
import com.eminus.settings.FarDistance;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkerHarness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class TreeManagerGiveUpTest {
    private static final int WORKER_THREADS = 1;
    private static final int RING_COLUMNS = 5;
    private static final int HEIGHT_CELLS = 2;
    private static final int ONE_CELL = 1;
    private static final int ONE_OCTANT = 0b1;
    private static final double EYE = FarDistance.BLOCKS_PER_TOP_LEVEL_CELL / 2.0;
    private static final double EYE_Y = 100.0;
    private static final long TOP = CellKey.pack(DetailLevel.MAX, 0, 1, 0);
    private static final long CHILD = CellKey.child(TOP, 0);
    private static final long AWAIT_MILLIS = 10_000L;
    private static final long POLL_MILLIS = 5L;
    private static final long PAST_THE_RETRY_MILLIS = 1_500L;

    private final FakeCellStore store = new FakeCellStore();
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final CellCache cache = new CellCache(store, handle -> { }, () -> 0L);
    private final FakeBuilds builds = new FakeBuilds();
    private final TreeManager manager =
            TreeManager.start(builds, new TreeExtent(new CellFrame(0), HEIGHT_CELLS, DetailLevel.MAX - 1));
    private final List<MeshSummary> roots = new ArrayList<>();
    private long sequence;

    @AfterEach
    void stopEverything() {
        manager.stop();
        harness.close();
    }

    @BeforeEach
    void requestTheChildOverTheCameraColumn() {
        manager.frame(close());
        for (int root = 0; root < RING_COLUMNS * HEIGHT_CELLS; root++) {
            FakeBuilds.Call call = builds.take();
            CellMesh mesh = TestMeshes.of(call.key(), call.key() == TOP ? ONE_OCTANT : OccupancyMask.EMPTY);
            roots.add(mesh.summary());
            manager.meshed(mesh, call.request());
        }
    }

    @Test
    void aCellWhoseBuildKeepsFailingIsAskedForNoMoreAndHidesNothingPastOrUnderIt() {
        failEveryAttempt();

        RenderList list = awaitRenderList(drawn -> drawn.meshes().containsAll(roots));
        assertEquals(roots.size(), list.meshes().size());

        walkFor(PAST_THE_RETRY_MILLIS);
        assertTrue(builds.idle());
    }

    @Test
    void aChangeOnACellThatWasGivenUpAsksForItAgainWithItsAttemptsStartedOver() {
        failEveryAttempt();
        awaitRenderList(drawn -> drawn.meshes().containsAll(roots));

        manager.changed(harness.call(() -> cache.open(CHILD)), FaceMask.NONE, EdgeMask.NONE, ONE_OCTANT,
                ++sequence);
        FakeBuilds.Call changed = builds.take();
        assertEquals(CHILD, changed.key());

        manager.failed(changed.key(), changed.request());
        assertEquals(CHILD, keepWalkingUntilACall().key());
    }

    private void failEveryAttempt() {
        manager.frame(close());
        FakeBuilds.Call attempt = builds.take();
        assertEquals(CHILD, attempt.key());

        for (int retry = 1; retry < FailedBuilds.ATTEMPTS; retry++) {
            manager.failed(attempt.key(), attempt.request());
            attempt = keepWalkingUntilACall();
            assertEquals(CHILD, attempt.key());
        }

        manager.failed(attempt.key(), attempt.request());
    }

    private FakeBuilds.Call keepWalkingUntilACall() {
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (builds.idle() && System.currentTimeMillis() < deadline) {
            manager.frame(close());
            sleep();
        }

        return builds.take();
    }

    private void walkFor(long millis) {
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            manager.frame(close());
            sleep();
        }
    }

    private RenderList awaitRenderList(Predicate<RenderList> ready) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);

        while (System.nanoTime() < deadline) {
            manager.frame(close());
            TreeBatch batch = manager.batches().take();
            RenderList list = batch == null ? null : batch.renderList();
            if (list != null && ready.test(list)) {
                return list;
            }

            sleep();
        }

        throw new IllegalStateException("The render list did not settle within " + AWAIT_MILLIS + " ms.");
    }

    private static CameraFrame close() {
        return FakeCameras.everything(EYE, EYE_Y, EYE, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
