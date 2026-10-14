package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.mesh.CellMesh;
import com.eminus.settings.FarDistance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TreeManagerFailureTest {
    private static final int RING_COLUMNS = 5;
    private static final int ONE_CELL = 1;
    private static final int ONE_OCTANT = 0b1;
    private static final double EYE = FarDistance.BLOCKS_PER_TOP_LEVEL_CELL / 2.0;
    private static final double EYE_Y = 100.0;
    private static final double ONE_BLOCK = 1.0;
    private static final long KEY = CellKey.pack(DetailLevel.MAX, 0, 0, 0);
    private static final long WEST = CellKey.pack(DetailLevel.MAX, -1, 0, 0);
    private static final long AWAIT_MILLIS = 10_000L;
    private static final long POLL_MILLIS = 5L;
    private static final long PAST_THE_RETRY_MILLIS = 1_500L;
    private static final long A_FEW_FRAMES_MILLIS = 100L;
    private static final int ONE_BUILDING = 1;
    private static final int NONE_BUILDING = 0;
    private static final int NONE_REQUESTED = 0;
    private static final long THIRD_WALK = 3L;

    private final FakeBuilds builds = new FakeBuilds();
    private final TreeManager manager =
            TreeManager.start(builds, new TreeExtent(new CellFrame(0), 1, DetailLevel.MAX - 1));

    @AfterEach
    void stopEverything() {
        manager.stop();
    }

    @Test
    void aFailedBuildFreesItsNodeAndBudgetAndIsRequestedAgain() {
        FakeBuilds.Call child = requestTheChild();
        await(builds::urgent);

        manager.failed(child.key(), child.request());
        await(() -> !builds.urgent());

        FakeBuilds.Call again = keepWalkingUntilACall(close(EYE));
        assertEquals(child.key(), again.key());
        assertNotEquals(child.request(), again.request());
    }

    @Test
    void aFailureOfAnOlderRequestLeavesTheNewerBuildAlone() {
        FakeBuilds.Call child = requestTheChild();
        manager.failed(child.key(), child.request());
        FakeBuilds.Call again = keepWalkingUntilACall(close(EYE));

        manager.failed(child.key(), child.request());
        walkFor(PAST_THE_RETRY_MILLIS, close(EYE));

        assertTrue(builds.idle());
        assertTrue(builds.urgent());
        manager.meshed(TestMeshes.of(again.key(), ONE_OCTANT), again.request());
        await(() -> !builds.urgent());
    }

    @Test
    void aFailedBuildWaitingForItsRetryLeavesTheTreeUnsettled() throws Exception {
        manager.frame(close(EYE));
        Map<Long, Long> roots = takeRoots();
        for (Map.Entry<Long, Long> root : roots.entrySet()) {
            manager.meshed(root.getKey() == KEY ? TestMeshes.of(KEY, ONE_OCTANT) : CellMesh.empty(root.getKey()),
                    root.getValue());
        }

        manager.frame(close(EYE));
        FakeBuilds.Call child = builds.take();
        manager.frame(close(EYE + ONE_BLOCK));
        await(() -> manager.walks() >= THIRD_WALK);
        TreeState before = awaitQuietState();
        assertEquals(ONE_BUILDING, before.buildingNodes());

        manager.failed(child.key(), child.request());
        TreeState during = awaitQuietState();
        assertEquals(NONE_BUILDING, during.buildingNodes());
        assertFalse(during.settled());
    }

    @Test
    void aFailedOutOfViewBuildIsRequestedAgainOutOfViewAndRaisesNoUrgency() {
        manager.frame(east());
        Map<Long, Long> roots = takeRoots();
        manager.meshed(CellMesh.empty(KEY), roots.get(KEY));
        manager.meshed(TestMeshes.of(WEST, ONE_OCTANT), roots.get(WEST));
        manager.frame(east());
        FakeBuilds.Call behind = builds.take();
        assertEquals(WEST, CellKey.parent(behind.key()));

        manager.failed(behind.key(), behind.request());
        FakeBuilds.Call again = keepWalkingUntilACall(east());
        assertEquals(behind.key(), again.key());

        walkFor(A_FEW_FRAMES_MILLIS, east());
        assertFalse(builds.urgent());
    }

    private FakeBuilds.Call requestTheChild() {
        manager.frame(close(EYE));
        Map<Long, Long> roots = takeRoots();

        assertFalse(builds.urgent());
        manager.meshed(TestMeshes.of(KEY, ONE_OCTANT), roots.get(KEY));
        manager.frame(close(EYE));
        return builds.take();
    }

    private Map<Long, Long> takeRoots() {
        Map<Long, Long> roots = new HashMap<>();
        for (int column = 0; column < RING_COLUMNS; column++) {
            FakeBuilds.Call call = builds.take();
            roots.put(call.key(), call.request());
        }

        return roots;
    }

    private FakeBuilds.Call keepWalkingUntilACall(CameraFrame camera) {
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (builds.idle() && System.currentTimeMillis() < deadline) {
            manager.frame(camera);
            sleep();
        }

        return builds.take();
    }

    private void walkFor(long millis, CameraFrame camera) {
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            manager.frame(camera);
            sleep();
        }
    }

    private TreeState awaitQuietState() throws Exception {
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        TreeState state = snapshot();
        while ((state.batchWaiting() || state.walkPending() || state.lastRequested() != NONE_REQUESTED)
                && System.currentTimeMillis() < deadline) {
            manager.batches().take();
            sleep();
            state = snapshot();
        }

        assertFalse(state.batchWaiting());
        assertFalse(state.walkPending());
        assertEquals(NONE_REQUESTED, state.lastRequested());
        return state;
    }

    private TreeState snapshot() throws Exception {
        return manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static CameraFrame close(double x) {
        return FakeCameras.everything(x, EYE_Y, EYE, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private static CameraFrame east() {
        return FakeCameras.looking(EYE, EYE_Y, EYE, 1.0F, 0.0F, 0.0F, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            sleep();
        }

        assertTrue(condition.getAsBoolean(), "The tree did not answer within " + AWAIT_MILLIS + " ms.");
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
