package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class TreeCleaner {
    static final int MESH_CAP = 32768;
    static final int PRESSURE_EVICTIONS = 64;
    static final int MAX_PER_WALK = 256;

    private final List<TreeNode> candidates = new ArrayList<>();

    List<TreeNode> pick(Collection<TreeNode> all, boolean pressure) {
        candidates.clear();

        for (TreeNode node : all) {
            if (!node.isRoot() && node.mesh() != null) {
                candidates.add(node);
            }
        }

        int wanted = Math.max(0, candidates.size() - MESH_CAP) + (pressure ? PRESSURE_EVICTIONS : 0);
        wanted = Math.min(Math.min(wanted, MAX_PER_WALK), candidates.size());
        if (wanted == 0) {
            return List.of();
        }

        candidates.sort(Comparator.comparingLong(TreeNode::lastSeen));
        return List.copyOf(candidates.subList(0, wanted));
    }
}
