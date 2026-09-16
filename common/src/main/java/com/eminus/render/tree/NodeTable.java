package com.eminus.render.tree;

import java.util.Collection;
import java.util.function.Consumer;

import com.eminus.cell.CellKey;
import com.eminus.cell.OccupancyMask;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import org.jspecify.annotations.Nullable;

public final class NodeTable {
    public static final int CAPACITY = 1 << 16;

    private final int capacity;
    private final Long2ObjectOpenHashMap<TreeNode> nodes = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<TreeNode> roots = new Long2ObjectOpenHashMap<>();

    public NodeTable(int capacity) {
        this.capacity = capacity;
    }

    public int size() {
        return nodes.size();
    }

    public int free() {
        return Math.max(0, capacity - nodes.size());
    }

    @Nullable TreeNode get(long key) {
        return nodes.get(key);
    }

    Collection<TreeNode> all() {
        return nodes.values();
    }

    Collection<TreeNode> roots() {
        return roots.values();
    }

    TreeNode root(long key) {
        TreeNode node = nodes.get(key);
        if (node == null) {
            node = TreeNode.root(key);
            nodes.put(key, node);
            roots.put(key, node);
        }

        return node;
    }

    @Nullable TreeNode child(TreeNode parent, int octant) {
        if (free() == 0) {
            return null;
        }

        long key = CellKey.child(parent.key(), octant);
        TreeNode child = new TreeNode(key, parent, octant);
        nodes.put(key, child);
        parent.attach(child);
        return child;
    }

    void remove(TreeNode node, Consumer<TreeNode> removed) {
        TreeNode parent = node.parent();
        if (parent == null) {
            roots.remove(node.key());
        } else {
            parent.detach(node.octant());
        }

        removeSubtree(node, removed);
    }

    private void removeSubtree(TreeNode node, Consumer<TreeNode> removed) {
        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            TreeNode child = node.child(octant);
            if (child != null) {
                removeSubtree(child, removed);
            }
        }

        nodes.remove(node.key());
        removed.accept(node);
    }
}
