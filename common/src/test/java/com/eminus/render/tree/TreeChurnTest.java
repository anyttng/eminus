package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;

import org.junit.jupiter.api.Test;

class TreeChurnTest {
    private static final double EYE = 0.0;
    private static final double PAST_RING_STEP = 128.0;
    private static final int CAPACITY = 64;

    private final NodeTable nodes = new NodeTable(CAPACITY);
    private final TreeChurn churn = new TreeChurn();
    private final TreeNode root = nodes.root(CellKey.pack(DetailLevel.MAX, 0, 0, 0));

    @Test
    void aMeshedNodeEvictedAndRequestedAgainCountsOnceAsRequestedAgain() {
        TreeNode child = meshedChild(0);
        churn.moved(EYE, EYE);
        churn.removed(child);
        churn.requested(child);
        churn.requested(child);

        Map<String, Long> reading = reading();
        assertEquals(1L, reading.get(TreeChurn.REQUESTED_AGAIN));
        assertEquals(1L, reading.get(TreeChurn.FIRST_BUILDS));
        assertEquals(0L, reading.get(TreeChurn.REMEMBERED));
    }

    @Test
    void aNodeEvictedBeforeItsMeshLandedIsNotRemembered() {
        TreeNode child = nodes.child(root, 0);
        churn.removed(child);
        churn.requested(child);

        assertEquals(0L, reading().get(TreeChurn.REQUESTED_AGAIN));
    }

    @Test
    void aRingStepForgetsWhatWasEvicted() {
        TreeNode child = meshedChild(0);
        churn.moved(EYE, EYE);
        churn.removed(child);
        churn.moved(PAST_RING_STEP, EYE);
        churn.requested(child);

        assertEquals(0L, reading().get(TreeChurn.REQUESTED_AGAIN));
        assertEquals(1L, reading().get(TreeChurn.FIRST_BUILDS));
    }

    @Test
    void anUnboundedHorizonReadsAsMinusOne() {
        assertEquals(TreeChurn.UNBOUNDED_HORIZON, reading().get(TreeChurn.HORIZON));
    }

    private TreeNode meshedChild(int octant) {
        TreeNode child = nodes.child(root, octant);
        child.meshed(TestMeshes.summary(child.key(), OccupancyMask.EMPTY));
        return child;
    }

    private Map<String, Long> reading() {
        return churn.reading(nodes.size(), nodes.capacity(), new TreeHorizon());
    }
}
