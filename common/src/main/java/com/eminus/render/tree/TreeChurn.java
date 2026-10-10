package com.eminus.render.tree;

import java.util.LinkedHashMap;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

final class TreeChurn {
    static final String REQUESTED_AGAIN = "requested_again";
    static final String FIRST_BUILDS = "first_builds";
    static final String EVICTED_UNWANTED = "evicted_unwanted";
    static final String EVICTED_FAR = "evicted_far";
    static final String REMEMBERED = "remembered";
    static final String NODES = "nodes";
    static final String CAPACITY = "capacity";
    static final String HORIZON = "horizon";
    static final String HORIZON_LOCKED = "horizon_locked";
    static final long UNBOUNDED_HORIZON = -1L;

    private static final double FORGET_BLOCKS_SQUARED = (double) TreeRing.MOVE_BLOCKS * TreeRing.MOVE_BLOCKS;

    private final LongOpenHashSet evictedBuilt = new LongOpenHashSet();

    private long requestedAgain;
    private long firstBuilds;
    private long evictedUnwanted;
    private long evictedFar;
    private boolean anchored;
    private double anchorX;
    private double anchorZ;

    void requested(TreeNode node) {
        if (evictedBuilt.remove(node.key())) {
            requestedAgain++;
        } else {
            firstBuilds++;
        }
    }

    void removed(TreeNode node) {
        if (node.mesh() != null) {
            evictedBuilt.add(node.key());
        }
    }

    void evicted(boolean unwanted) {
        if (unwanted) {
            evictedUnwanted++;
        } else {
            evictedFar++;
        }
    }

    void moved(double eyeX, double eyeZ) {
        double dx = eyeX - anchorX;
        double dz = eyeZ - anchorZ;
        if (!anchored || dx * dx + dz * dz >= FORGET_BLOCKS_SQUARED) {
            anchored = true;
            anchorX = eyeX;
            anchorZ = eyeZ;
            evictedBuilt.clear();
        }
    }

    Map<String, Long> reading(int nodes, int capacity, TreeHorizon horizon) {
        Map<String, Long> reading = new LinkedHashMap<>();
        reading.put(REQUESTED_AGAIN, requestedAgain);
        reading.put(FIRST_BUILDS, firstBuilds);
        reading.put(EVICTED_UNWANTED, evictedUnwanted);
        reading.put(EVICTED_FAR, evictedFar);
        reading.put(REMEMBERED, (long) evictedBuilt.size());
        reading.put(NODES, (long) nodes);
        reading.put(CAPACITY, (long) capacity);
        reading.put(HORIZON, horizon.horizon() == TreeHorizon.UNBOUNDED
                ? UNBOUNDED_HORIZON : Math.round(horizon.horizon()));
        reading.put(HORIZON_LOCKED, horizon.locked() ? 1L : 0L);
        return reading;
    }
}
