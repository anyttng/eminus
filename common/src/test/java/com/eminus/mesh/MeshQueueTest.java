package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eminus.cell.CellKey;

import org.junit.jupiter.api.Test;

class MeshQueueTest {
    @Test
    void aCoarseTaskQueuedLaterStillRunsFirst() {
        MeshQueue queue = new MeshQueue();
        MeshTask fine = MeshTask.fresh(CellKey.pack(0, 1, 2, 3));
        MeshTask coarse = MeshTask.fresh(CellKey.pack(4, 1, 2, 3));

        queue.add(fine);
        queue.add(coarse);

        assertEquals(coarse, queue.poll());
        assertEquals(fine, queue.poll());
    }

    @Test
    void aRetriedTaskRunsAfterTheFreshTasksOfItsLevel() {
        MeshQueue queue = new MeshQueue();
        MeshTask retried = MeshTask.fresh(CellKey.pack(2, 0, 0, 0)).retry();
        MeshTask fresh = MeshTask.fresh(CellKey.pack(2, 9, 9, 9));

        queue.add(retried);
        queue.add(fresh);

        assertEquals(fresh, queue.poll());
        assertEquals(retried, queue.poll());
    }

    @Test
    void aRetriedTaskStillRunsBeforeAnyFinerLevel() {
        MeshQueue queue = new MeshQueue();
        MeshTask retriedCoarse = MeshTask.fresh(CellKey.pack(3, 0, 0, 0)).retry();
        MeshTask freshFine = MeshTask.fresh(CellKey.pack(1, 0, 0, 0));

        queue.add(freshFine);
        queue.add(retriedCoarse);

        assertEquals(retriedCoarse, queue.poll());
        assertEquals(freshFine, queue.poll());
    }

    @Test
    void tasksOfOneClassComeBackInTheOrderTheyArrived() {
        MeshQueue queue = new MeshQueue();
        MeshTask first = MeshTask.fresh(CellKey.pack(1, 1, 0, 0));
        MeshTask second = MeshTask.fresh(CellKey.pack(1, 2, 0, 0));
        MeshTask third = MeshTask.fresh(CellKey.pack(1, 3, 0, 0));

        queue.add(first);
        queue.add(second);
        queue.add(third);

        assertEquals(first, queue.poll());
        assertEquals(second, queue.poll());
        assertEquals(third, queue.poll());
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
}
