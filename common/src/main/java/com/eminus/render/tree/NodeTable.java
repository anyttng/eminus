package com.eminus.render.tree;

import java.util.Collection;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import org.jspecify.annotations.Nullable;

public final class NodeTable {
    private final Long2ObjectOpenHashMap<TreeNode> nodes = new Long2ObjectOpenHashMap<>();

    public int size() {
        return nodes.size();
    }

    TreeNode node(long key) {
        return nodes.computeIfAbsent(key, TreeNode::new);
    }

    @Nullable TreeNode get(long key) {
        return nodes.get(key);
    }

    Collection<TreeNode> all() {
        return nodes.values();
    }
}
