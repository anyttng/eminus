package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.eminus.cell.CellFrame;
import com.eminus.cell.DetailLevel;
import com.eminus.settings.FarDistance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TreeManagerThrowTest {
    private static final int ONE_CELL = 1;
    private static final double EYE = FarDistance.BLOCKS_PER_TOP_LEVEL_CELL / 2.0;
    private static final double EYE_Y = 100.0;
    private static final long AWAIT_MILLIS = 5_000L;

    private final FakeBuilds builds = new FakeBuilds();
    private final TreeManager manager =
            TreeManager.start(builds, new TreeExtent(new CellFrame(0), 1, DetailLevel.MAX - 1));

    @AfterEach
    void stopEverything() {
        manager.stop();
    }

    @Test
    void aThrowWhileAFrameIsAppliedLeavesTheTreeAnsweringASnapshot() throws Exception {
        builds.throwOnTheNextBuild();
        manager.frame(FakeCameras.everything(EYE, EYE_Y, EYE, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK));

        assertNotNull(manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS));
    }

    @Test
    void aSnapshotWhoseApplicationThrowsFailsAndTheNextOneAnswers() throws Exception {
        builds.throwOnTheNextBacklog();

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS));
        assertEquals(FakeBuilds.STAGED_THROW, failure.getCause().getMessage());
        assertNotNull(manager.snapshot().get(AWAIT_MILLIS, TimeUnit.MILLISECONDS));
    }
}
