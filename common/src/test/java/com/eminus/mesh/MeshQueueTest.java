package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eminus.cell.CellKey;

import org.junit.jupiter.api.Test;

class MeshQueueTest {
    private static final float SMALL = 10.0F;
    private static final float LARGE = 500.0F;

    @Test
    void aLargerPriorityRunsFirstWhateverItsLevel() {
        MeshQueue queue = new MeshQueue();
        MeshTask farRoot = task(CellKey.pack(4, 1, 2, 3), SMALL);
        MeshTask nearFine = task(CellKey.pack(1, 1, 2, 3), LARGE);

        queue.add(farRoot);
        queue.add(nearFine);

        assertEquals(nearFine, queue.poll());
        assertEquals(farRoot, queue.poll());
    }

    @Test
    void aRetriedTaskRunsAfterTheFreshTasksOfItsPriority() {
        MeshQueue queue = new MeshQueue();
        MeshTask retried = task(CellKey.pack(2, 0, 0, 0), SMALL).retry();
        MeshTask fresh = task(CellKey.pack(2, 9, 9, 9), SMALL);

        queue.add(retried);
        queue.add(fresh);

        assertEquals(fresh, queue.poll());
        assertEquals(retried, queue.poll());
    }

    @Test
    void aRetriedTaskKeepsItsPriorityAndStillRunsBeforeASmallerOne() {
        MeshQueue queue = new MeshQueue();
        MeshTask retriedLarge = task(CellKey.pack(1, 0, 0, 0), LARGE).retry();
        MeshTask freshSmall = task(CellKey.pack(3, 0, 0, 0), SMALL);

        queue.add(freshSmall);
        queue.add(retriedLarge);

        assertEquals(retriedLarge, queue.poll());
        assertEquals(freshSmall, queue.poll());
    }

    @Test
    void tasksOfOnePriorityComeBackInTheOrderTheyArrived() {
        MeshQueue queue = new MeshQueue();
        MeshTask first = MeshTask.fresh(CellKey.pack(1, 1, 0, 0));
        MeshTask second = MeshTask.fresh(CellKey.pack(4, 2, 0, 0));
        MeshTask third = MeshTask.fresh(CellKey.pack(0, 3, 0, 0));

        queue.add(first);
        queue.add(second);
        queue.add(third);

        assertEquals(first, queue.poll());
        assertEquals(second, queue.poll());
        assertEquals(third, queue.poll());
    }

    @Test
    void anOlderSmallerTaskRunsBeforeANewerLargerOnceItsDelayHasPassed() {
        MeshQueue queue = new MeshQueue();
        MeshTask older = queuedAt(CellKey.pack(0, 1, 0, 0), 100.0F, 0L);
        MeshTask newer = queuedAt(CellKey.pack(2, 1, 0, 0), 1000.0F, 200_000_000L);

        queue.add(newer);
        queue.add(older);

        assertEquals(older, queue.poll());
        assertEquals(newer, queue.poll());
    }

    @Test
    void anOutOfViewTaskRunsAfterEveryInViewTaskHoweverLongItWaits() {
        MeshQueue queue = new MeshQueue();
        MeshTask outOfView = queuedAt(CellKey.pack(1, 1, 0, 0), -0.01F, 0L);
        MeshTask horizon = queuedAt(CellKey.pack(4, 1, 0, 0), 0.001F, 1_000_000_000L);

        queue.add(outOfView);
        queue.add(horizon);

        assertEquals(horizon, queue.poll());
        assertEquals(outOfView, queue.poll());
    }

    @Test
    void anEmptyQueueAnswersWithNothingAndKeepsItsSizeStraight() {
        MeshQueue queue = new MeshQueue();
        assertNull(queue.poll());

        queue.add(MeshTask.fresh(CellKey.pack(0, 0, 0, 0)));
        assertEquals(1, queue.size());

        queue.poll();
        assertEquals(0, queue.size());
        assertNull(queue.poll());
    }

    private static MeshTask task(long key, float priority) {
        return MeshTask.carrying(key, null, 0, MeshTask.NO_REQUEST, priority);
    }

    private static MeshTask queuedAt(long key, float priority, long queued) {
        return new MeshTask(key, false, null, 0, MeshTask.NO_REQUEST, priority, queued);
    }
}
