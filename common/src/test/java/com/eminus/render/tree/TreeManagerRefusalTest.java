package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.mesh.CellMesh;
import com.eminus.settings.FarDistance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class TreeManagerRefusalTest {
    private static final int RING_COLUMNS = 5;
    private static final int ONE_CELL = 1;
    private static final int ONE_OCTANT = 0b1;
    private static final double EYE = FarDistance.BLOCKS_PER_TOP_LEVEL_CELL / 2.0;
    private static final double EYE_Y = 100.0;
    private static final long KEY = CellKey.pack(DetailLevel.MAX, 0, 0, 0);
    private static final long AWAIT_MILLIS = 10_000L;
    private static final long A_FEW_FRAMES_MILLIS = 100L;
    private static final long POLL_MILLIS = 5L;
    private static final long YES = 1L;
    private static final long NO = 0L;
    private static final boolean KEPT = true;
    private static final boolean DROPPED = false;

    private final FakeBuilds builds = new FakeBuilds();
    private final TreeManager manager =
            TreeManager.start(builds, new TreeExtent(new CellFrame(0), 1, DetailLevel.MAX - 1));
    private FakeBuilds.Call child;

    @BeforeEach
    void meshTheChild() throws Exception {
        manager.frame(close());
        Map<Long, Long> roots = takeRoots();
        for (Map.Entry<Long, Long> root : roots.entrySet()) {
            manager.meshed(root.getKey() == KEY ? TestMeshes.of(KEY, ONE_OCTANT) : CellMesh.empty(root.getKey()),
                    root.getValue());
        }

        manager.frame(close());
        child = builds.take();
        manager.meshed(TestMeshes.of(child.key(), ONE_OCTANT), child.request());
        assertEquals(YES, row(KEY)[NodeRow.CHILDREN_READY]);
    }

    @AfterEach
    void stopEverything() {
        manager.stop();
    }

    @Test
    void aRefusedFirstMeshLeavesItsParentNotReadyAndIsRebuiltOnAFrameWithoutPressure() throws Exception {
        manager.refused(child.key(), child.request(), DROPPED);

        assertEquals(NO, row(KEY)[NodeRow.CHILDREN_READY]);
        assertEquals(NO, row(child.key())[NodeRow.MESHED]);
        FakeBuilds.Call again = keepWalkingUntilACall(close());
        assertEquals(child.key(), again.key());
        assertNotEquals(child.request(), again.request());
    }

    @Test
    void aRefusedReplacementKeepsItsParentReadyAndIsRebuiltOnAFrameWithoutPressure() throws Exception {
        manager.refused(child.key(), child.request(), KEPT);

        assertEquals(YES, row(KEY)[NodeRow.CHILDREN_READY]);
        assertEquals(YES, row(child.key())[NodeRow.MESHED]);
        FakeBuilds.Call again = keepWalkingUntilACall(close());
        assertEquals(child.key(), again.key());
        assertNotEquals(child.request(), again.request());
    }

    @Test
    void aRefusedMeshIsNotRebuiltWhileThePressureHolds() throws Exception {
        manager.refused(child.key(), child.request(), KEPT);
        row(KEY);

        walkFor(A_FEW_FRAMES_MILLIS, FakeCameras.underPressure(close()));
        assertTrue(builds.idle());
    }

    @Test
    void aRefusalOfAnotherRequestChangesNothing() throws Exception {
        manager.refused(child.key(), child.request() + 1, DROPPED);

        assertEquals(YES, row(KEY)[NodeRow.CHILDREN_READY]);
        walkFor(A_FEW_FRAMES_MILLIS, close());
        assertTrue(builds.idle());
    }

    private long[] row(long key) throws Exception {
        long[] asked = new long[NodeRow.WIDTH];
        asked[NodeRow.LEVEL] = CellKey.level(key);
        asked[NodeRow.CELL_X] = CellKey.x(key);
        asked[NodeRow.CELL_Y] = CellKey.y(key);
        asked[NodeRow.CELL_Z] = CellKey.z(key);
        return manager.describe(List.of(asked)).get(AWAIT_MILLIS, TimeUnit.MILLISECONDS).getFirst();
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
