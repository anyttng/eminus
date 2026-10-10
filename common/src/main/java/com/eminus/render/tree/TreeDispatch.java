package com.eminus.render.tree;

import com.eminus.cell.cache.CellHandle;

import org.jspecify.annotations.Nullable;

final class TreeDispatch {
    private final TreeBuilds builds;
    private final TreeExtent extent;
    private final CellBox box = new CellBox();

    private long requests;
    private int refinements;
    private int outOfViewRefinements;

    TreeDispatch(TreeBuilds builds, TreeExtent extent) {
        this.builds = builds;
        this.extent = extent;
    }

    int budget(int free) {
        return Math.min(RequestBudget.perWalk(refinements), free);
    }

    int outOfViewBudget(int free) {
        return Math.min(RequestBudget.perWalk(refinements + outOfViewRefinements), free);
    }

    int inFlight() {
        return refinements + outOfViewRefinements;
    }

    float priority(TreeNode node, @Nullable CameraFrame camera) {
        return camera == null
                ? ProjectedSize.UNKNOWN
                : ProjectedSize.of(box.set(extent.frame(), node.key(), camera), camera.pixelsPerBlock());
    }

    void build(TreeNode node, float priority, boolean outOfView) {
        if (!node.isRoot() && !node.building()) {
            if (outOfView) {
                outOfViewRefinements++;
            } else {
                refinements++;
            }
        }

        CellHandle handle = node.pending();
        int references = node.pendingReferences();
        long request = ++requests;
        node.clearPending();
        node.startBuild(request, outOfView);
        builds.build(node.key(), handle, references, request, priority);
    }

    void settle(TreeNode node) {
        if (node.outOfViewBuild()) {
            outOfViewRefinements--;
        } else {
            refinements--;
        }
    }
}
