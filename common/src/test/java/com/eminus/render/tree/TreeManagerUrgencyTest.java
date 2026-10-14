package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

class TreeManagerUrgencyTest {
    private static final int RING_COLUMNS = 5;
    private static final int ONE_CELL = 1;
    private static final int ONE_OCTANT = 0b1;
    private static final double EYE = FarDistance.BLOCKS_PER_TOP_LEVEL_CELL / 2.0;
    private static final double EYE_Y = 100.0;
    private static final double ONE_BLOCK = 1.0;
    private static final long KEY = CellKey.pack(DetailLevel.MAX, 0, 0, 0);
    private static final long AWAIT_MILLIS = 10_000L;
    private static final long POLL_MILLIS = 5L;

    private final FakeBuilds builds = new FakeBuilds();
    private final TreeManager manager =
            TreeManager.start(builds, new TreeExtent(new CellFrame(0), 1, DetailLevel.MAX - 1));

    @AfterEach
    void stopEverything() {
        manager.stop();
    }

    @Test
    void buildsAreUrgentWhileAChildInViewIsBuildingAndNoLonger() {
        manager.frame(close(EYE));
        Map<Long, Long> roots = new HashMap<>();
        for (int column = 0; column < RING_COLUMNS; column++) {
            FakeBuilds.Call call = builds.take();
            roots.put(call.key(), call.request());
        }

        assertFalse(builds.urgent());
        manager.meshed(TestMeshes.of(KEY, ONE_OCTANT), roots.get(KEY));
        manager.frame(close(EYE + ONE_BLOCK));
        FakeBuilds.Call child = builds.take();
        await(builds::urgent);

        manager.meshed(TestMeshes.of(child.key(), ONE_OCTANT), child.request());
        manager.frame(close(EYE + 2 * ONE_BLOCK));
        await(() -> !builds.urgent());
    }

    private static CameraFrame close(double x) {
        return FakeCameras.everything(x, EYE_Y, EYE, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK);
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        assertTrue(condition.getAsBoolean(), "The urgency did not turn within " + AWAIT_MILLIS + " ms.");
    }
}
