package com.eminus.render.tree;

import java.util.List;

import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;

final class RefusedMeshes {
    private final ReferenceLinkedOpenHashSet<TreeNode> nodes = new ReferenceLinkedOpenHashSet<>();

    boolean isEmpty() {
        return nodes.isEmpty();
    }

    void add(TreeNode node) {
        nodes.add(node);
    }

    List<TreeNode> take() {
        List<TreeNode> taken = List.copyOf(nodes);
        nodes.clear();
        return taken;
    }
}
