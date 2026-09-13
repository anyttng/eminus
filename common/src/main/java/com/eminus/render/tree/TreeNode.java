package com.eminus.render.tree;

import com.eminus.cell.CellKey;
import com.eminus.cell.OccupancyMask;
import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.CellMesh;

import org.jspecify.annotations.Nullable;

public final class TreeNode {
    static final int NO_OCTANT = -1;

    private final long key;
    private final int level;
    private final @Nullable TreeNode parent;
    private final int octant;

    private @Nullable TreeNode @Nullable [] children;
    private @Nullable CellMesh mesh;
    private @Nullable CellHandle pending;
    private int pendingReferences;
    private int requestedOctants;
    private int descendedOccupancy;
    private long lastSeen;
    private long request;
    private boolean building;
    private boolean rebuild;

    TreeNode(long key, @Nullable TreeNode parent, int octant) {
        this.key = key;
        this.level = CellKey.level(key);
        this.parent = parent;
        this.octant = octant;
    }

    static TreeNode root(long key) {
        return new TreeNode(key, null, NO_OCTANT);
    }

    public long key() {
        return key;
    }

    public int level() {
        return level;
    }

    public @Nullable CellMesh mesh() {
        return mesh;
    }

    public boolean isRoot() {
        return parent == null;
    }

    @Nullable TreeNode parent() {
        return parent;
    }

    int octant() {
        return octant;
    }

    int occupancy() {
        return mesh == null ? OccupancyMask.EMPTY : mesh.occupancy();
    }

    @Nullable TreeNode child(int at) {
        return children == null ? null : children[at];
    }

    int requestedOctants() {
        return requestedOctants;
    }

    int missingOctants() {
        return occupancy() & ~requestedOctants;
    }

    boolean childrenReady() {
        int occupied = occupancy();
        return meshedIn(descendedOccupancy == OccupancyMask.EMPTY ? occupied : occupied & descendedOccupancy);
    }

    void markDescended() {
        int occupied = occupancy();
        if (meshedIn(occupied)) {
            descendedOccupancy = occupied;
        }
    }

    private boolean meshedIn(int octants) {
        for (int at = 0; at < OccupancyMask.OCTANTS; at++) {
            if (!OccupancyMask.isSet(octants, at)) {
                continue;
            }

            TreeNode child = child(at);
            if (child == null || child.mesh == null) {
                return false;
            }
        }

        return true;
    }

    void attach(TreeNode child) {
        if (children == null) {
            children = new TreeNode[OccupancyMask.OCTANTS];
        }

        children[child.octant] = child;
        requestedOctants = OccupancyMask.set(requestedOctants, child.octant);
    }

    void detach(int at) {
        if (children != null) {
            children[at] = null;
        }

        requestedOctants = OccupancyMask.clear(requestedOctants, at);
    }

    long lastSeen() {
        return lastSeen;
    }

    void seen(long walk) {
        lastSeen = walk;
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

    long request() {
        return request;
    }

    void startBuild(long dispatched) {
        request = dispatched;
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
