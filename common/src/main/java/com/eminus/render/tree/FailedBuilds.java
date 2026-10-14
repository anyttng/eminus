package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.List;

final class FailedBuilds {
    private static final long RETRY_NANOS = 1_000_000_000L;

    private final List<TreeNode> nodes = new ArrayList<>();
    private long retryAt;

    boolean isEmpty() {
        return nodes.isEmpty();
    }

    void add(TreeNode node, long now) {
        if (nodes.isEmpty()) {
            retryAt = now + RETRY_NANOS;
        }

        nodes.add(node);
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
