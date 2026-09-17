package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class TreeCleaner {
    static final int PRESSURE_EVICTIONS = 64;

    private final List<TreeNode> candidates = new ArrayList<>();

    List<TreeNode> pick(Collection<TreeNode> all, boolean pressure, long walk) {
        if (!pressure) {
            return List.of();
        }

        candidates.clear();

        for (TreeNode node : all) {
            if (!node.isRoot() && node.mesh() != null && node.lastSeen() < walk) {
                candidates.add(node);
            }
        }

        int wanted = Math.min(PRESSURE_EVICTIONS, candidates.size());
        if (wanted == 0) {
            return List.of();
        }

        candidates.sort(Comparator.comparingLong(TreeNode::lastSeen));
        return List.copyOf(candidates.subList(0, wanted));
    }
}
