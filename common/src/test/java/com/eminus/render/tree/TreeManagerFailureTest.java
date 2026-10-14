package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.settings.FarDistance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TreeManagerFailureTest {
    private static final int RING_COLUMNS = 5;
    private static final int ONE_CELL = 1;
    private static final int ONE_OCTANT = 0b1;
    private static final double EYE = FarDistance.BLOCKS_PER_TOP_LEVEL_CELL / 2.0;
    private static final double EYE_Y = 100.0;
    private static final long KEY = CellKey.pack(DetailLevel.MAX, 0, 0, 0);
    private static final long AWAIT_MILLIS = 10_000L;
    private static final long POLL_MILLIS = 5L;
    private static final long PAST_THE_RETRY_MILLIS = 1_500L;

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

        FakeBuilds.Call again = keepWalkingUntilACall();
        assertEquals(child.key(), again.key());
        assertNotEquals(child.request(), again.request());
    }

    @Test
    void aFailureOfAnOlderRequestLeavesTheNewerBuildAlone() {
        FakeBuilds.Call child = requestTheChild();
        manager.failed(child.key(), child.request());
        FakeBuilds.Call again = keepWalkingUntilACall();

        manager.failed(child.key(), child.request());
        walkFor(PAST_THE_RETRY_MILLIS);

        assertTrue(builds.idle());
        assertTrue(builds.urgent());
        manager.meshed(TestMeshes.of(again.key(), ONE_OCTANT), again.request());
        await(() -> !builds.urgent());
    }

    private FakeBuilds.Call requestTheChild() {
        manager.frame(close());
        Map<Long, Long> roots = new HashMap<>();
        for (int column = 0; column < RING_COLUMNS; column++) {
            FakeBuilds.Call call = builds.take();
            roots.put(call.key(), call.request());
        }

        assertFalse(builds.urgent());
        manager.meshed(TestMeshes.of(KEY, ONE_OCTANT), roots.get(KEY));
        manager.frame(close());
        return builds.take();
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

    private static CameraFrame close() {
        return FakeCameras.everything(EYE, EYE_Y, EYE, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
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
