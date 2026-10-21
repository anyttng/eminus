package com.eminus.render.tree;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.EdgeMask;
import com.eminus.cell.FaceMask;
import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.FluidCorners;
import com.eminus.mesh.MeshSummary;
import com.eminus.settings.FarDistance;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

final class TreeChanges {
    private static final Direction[] FACES = Direction.values();
    private static final int ONE_REFERENCE = 1;

    private final NodeTable nodes;
    private final TreeExtent extent;
    private final TreeBuilds builds;
    private final TreeDispatch dispatch;

    TreeChanges(NodeTable nodes, TreeExtent extent, TreeBuilds builds, TreeDispatch dispatch) {
        this.nodes = nodes;
        this.extent = extent;
        this.builds = builds;
        this.dispatch = dispatch;
    }

    boolean changed(CellHandle handle, int faceMask, int edgeMask, int occupancy, long sequence,
            @Nullable CameraFrame camera) {
        long key = handle.key();
        TreeNode node = nodes.get(key);
        boolean walkDue = false;
        if (node == null) {
            builds.release(handle, ONE_REFERENCE);
        } else {
            node.hold(handle);
            walkDue = node.changeOccupancy(occupancy, sequence) && node.gaveWay();
            requestBuild(node, camera);
        }

        requestNeighbors(key, faceMask, camera);
        if ((faceMask & FaceMask.UP) == 0) {
            TreeNode above = nodes.get(CellKey.neighbor(key, Direction.UP));
            MeshSummary aboveMesh = above == null ? null : above.mesh();
            if (above != null && aboveMesh != null && aboveMesh.readsBelow()) {
                requestBuild(above, camera);
            }
        }

        if (CellKey.level(key) == FluidCorners.LEVEL) {
            requestAcrossEdges(key, edgeMask, camera);
        }

        return walkDue;
    }

    void defer(TreeNode node) {
        node.markStale();
        CellHandle pending = node.pending();
        if (pending != null) {
            builds.release(pending, node.pendingReferences());
            node.clearPending();
        }
    }

    void covered(int chunkX, int chunkZ, @Nullable CameraFrame camera) {
        int firstBlockX = chunkX * FarDistance.BLOCKS_PER_CHUNK;
        int firstBlockZ = chunkZ * FarDistance.BLOCKS_PER_CHUNK;

        for (int level = extent.lowestLevel(); level <= DetailLevel.MAX; level++) {
            int heightCells = extent.heightCells() << (DetailLevel.MAX - level);
            int lastCellX = CellFrame.cellX(firstBlockX + FarDistance.BLOCKS_PER_CHUNK, level);
            int lastCellZ = CellFrame.cellZ(firstBlockZ + FarDistance.BLOCKS_PER_CHUNK, level);

            for (int cellX = CellFrame.cellX(firstBlockX - 1, level); cellX <= lastCellX; cellX++) {
                for (int cellZ = CellFrame.cellZ(firstBlockZ - 1, level); cellZ <= lastCellZ; cellZ++) {
                    for (int cellY = 0; cellY < heightCells; cellY++) {
                        TreeNode node = nodes.get(CellKey.pack(level, cellX, cellY, cellZ));
                        if (node != null) {
                            requestBuild(node, camera);
                        }
                    }
                }
            }
        }
    }

    private void requestNeighbors(long key, int faceMask, @Nullable CameraFrame camera) {
        for (Direction face : FACES) {
            if ((faceMask & FaceMask.bit(face)) == 0) {
                continue;
            }

            TreeNode neighbor = nodes.get(CellKey.neighbor(key, face));
            if (neighbor != null) {
                requestBuild(neighbor, camera);
            }
        }
    }

    private void requestAcrossEdges(long key, int edgeMask, @Nullable CameraFrame camera) {
        int remaining = edgeMask;

        while (remaining != 0) {
            int slot = Integer.numberOfTrailingZeros(remaining);
            TreeNode neighbor = nodes.get(CellKey.pack(CellKey.level(key), CellKey.x(key) + EdgeMask.stepX(slot),
                    CellKey.y(key) + EdgeMask.stepY(slot), CellKey.z(key) + EdgeMask.stepZ(slot)));
            if (neighbor != null) {
                requestBuild(neighbor, camera);
            }

            remaining &= remaining - 1;
        }
    }

    private void requestBuild(TreeNode node, @Nullable CameraFrame camera) {
        if (node.building()) {
            node.markRebuild();
            return;
        }

        if (node.gaveWay()) {
            defer(node);
            return;
        }

        dispatch.build(node, dispatch.priority(node, camera), false);
    }
}
