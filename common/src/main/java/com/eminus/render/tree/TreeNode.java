package com.eminus.render.tree;

import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.CellMesh;

import org.jspecify.annotations.Nullable;

public final class TreeNode {
    private final long key;

    private @Nullable CellMesh mesh;
    private @Nullable CellHandle pending;
    private int pendingReferences;
    private boolean building;
    private boolean rebuild;

    TreeNode(long key) {
        this.key = key;
    }

    public long key() {
        return key;
    }

    public @Nullable CellMesh mesh() {
        return mesh;
    }

    boolean building() {
        return building;
    }

    // Every hold on one cell returns the same handle, so the references are counted rather than listed.
    void hold(CellHandle handle) {
        if (pending == null) {
            pending = handle;
        }

        pendingReferences++;
    }

    @Nullable CellHandle pending() {
        return pending;
    }

    int pendingReferences() {
        return pendingReferences;
    }

    void clearPending() {
        pending = null;
        pendingReferences = 0;
    }

    void startBuild() {
        building = true;
    }

    void meshed(CellMesh built) {
        mesh = built;
        building = false;
    }

    void markRebuild() {
        rebuild = true;
    }

    boolean takeRebuild() {
        boolean due = rebuild;
        rebuild = false;
        return due;
    }
}
