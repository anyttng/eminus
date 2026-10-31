package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.List;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;

final class FailedBuilds {
    static final int ATTEMPTS = 3;

    private static final long RETRY_NANOS = 1_000_000_000L;

    private final List<TreeNode> nodes = new ArrayList<>();
    private long retryAt;

    boolean isEmpty() {
        return nodes.isEmpty();
    }

    boolean add(TreeNode node, int failures, long now) {
        if (failures >= ATTEMPTS) {
            long key = node.key();
            Eminus.LOGGER.warn("Meshing cell {} at level {} ({}, {}, {}) failed {} times, so the tree asks for it "
                    + "again only once it changes", key, CellKey.level(key), CellKey.x(key), CellKey.y(key),
                    CellKey.z(key), failures);
            return false;
        }

        if (nodes.isEmpty()) {
            retryAt = now + RETRY_NANOS;
        }

        nodes.add(node);
        return true;
    }

    boolean due(long now) {
        return !nodes.isEmpty() && now - retryAt >= 0;
    }

    List<TreeNode> take() {
        List<TreeNode> taken = List.copyOf(nodes);
        nodes.clear();
        return taken;
    }
}
