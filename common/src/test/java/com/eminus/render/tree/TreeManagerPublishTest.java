package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.settings.FarDistance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TreeManagerPublishTest {
    private static final int RING_COLUMNS = 5;
    private static final int ONE_CELL = 1;
    private static final int ONE_OCTANT = 0b1;
    private static final double EYE = FarDistance.BLOCKS_PER_TOP_LEVEL_CELL / 2.0;
    private static final double EYE_Y = 100.0;
    private static final long KEY = CellKey.pack(DetailLevel.MAX, 0, 0, 0);
    private static final int DESCRIBED_ROWS = 20_000;
    private static final int QUEUED_DESCRIBES = 64;
    private static final long AWAIT_MILLIS = 3_000L;
    private static final long POLL_MILLIS = 5L;

    private final FakeBuilds builds = new FakeBuilds();
    private final TreeManager manager =
            TreeManager.start(builds, new TreeExtent(new CellFrame(0), 1, DetailLevel.MAX - 1));

    @AfterEach
    void stopEverything() {
        manager.stop();
    }

    @Test
    void aMeshIsPublishedWhileTheQueueNeverEmpties() throws InterruptedException {
        manager.frame(FakeCameras.everything(EYE, EYE_Y, EYE, ONE_CELL, FakeCameras.CLOSE_PIXELS_PER_BLOCK));
        Map<Long, Long> roots = new HashMap<>();
        for (int column = 0; column < RING_COLUMNS; column++) {
            FakeBuilds.Call call = builds.take();
            roots.put(call.key(), call.request());
        }

        assertNotNull(await(), "The first walk's batch was not published.");
        manager.batches().take();

        AtomicBoolean flooding = new AtomicBoolean(true);
        CountDownLatch filled = new CountDownLatch(1);
        Thread flood = new Thread(() -> keepQueued(flooding, filled));
        flood.start();
        try {
            assertTrue(filled.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "The tree's queue was never filled.");
            manager.meshed(TestMeshes.of(KEY, ONE_OCTANT), roots.get(KEY));
            assertNotNull(await(), "No batch was published within " + AWAIT_MILLIS
                    + " ms while the tree's queue kept " + QUEUED_DESCRIBES + " messages waiting.");
        } finally {
            flooding.set(false);
            flood.join();
        }
    }

    private void keepQueued(AtomicBoolean flooding, CountDownLatch filled) {
        List<long[]> rows = new ArrayList<>();
        for (int row = 0; row < DESCRIBED_ROWS; row++) {
            rows.add(new long[NodeRow.WIDTH]);
        }

        Deque<CompletableFuture<List<long[]>>> queued = new ArrayDeque<>();
        while (flooding.get()) {
            if (queued.size() < QUEUED_DESCRIBES) {
                queued.add(manager.describe(rows));
            } else {
                filled.countDown();
                queued.poll().join();
            }
        }
    }

    private TreeBatch await() {
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (manager.batches().peek() == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        return manager.batches().peek();
    }
}
