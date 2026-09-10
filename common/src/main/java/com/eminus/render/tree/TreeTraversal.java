package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;
import com.eminus.mesh.CellMesh;
import com.eminus.settings.FarDistance;

import org.joml.FrustumIntersection;

final class TreeTraversal {
    private static final double INSIDE_DISTANCE = 1.0;

    private final NodeTable nodes;
    private final TreeExtent extent;
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final List<TreeNode> current = new ArrayList<>();
    private final List<TreeNode> next = new ArrayList<>();
    private final List<CellMesh> drawn = new ArrayList<>();
    private final List<TreeNode> requested = new ArrayList<>();

    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;

    TreeTraversal(NodeTable nodes, TreeExtent extent) {
        this.nodes = nodes;
        this.extent = extent;
    }

    RenderList walk(Collection<TreeNode> roots, CameraFrame camera, int budget, long walk) {
        frustum.set(camera.viewProjection());
        current.clear();
        current.addAll(roots);
        drawn.clear();
        requested.clear();
        double farBlocks = (double) camera.farCells() * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL;

        while (!current.isEmpty()) {
            next.clear();

            for (TreeNode node : current) {
                budget = visit(node, camera, farBlocks, budget, walk);
            }

            current.clear();
            current.addAll(next);
        }

        return new RenderList(List.copyOf(drawn));
    }

    // Reused by the next walk; consumed before it.
    List<TreeNode> requested() {
        return requested;
    }

    private int visit(TreeNode node, CameraFrame camera, double farBlocks, int budget, long walk) {
        box(node, camera);

        double horizontal = Math.sqrt(axisDistanceSquared(minX, maxX) + axisDistanceSquared(minZ, maxZ));
        if (horizontal > farBlocks) {
            return budget;
        }

        if (!frustum.testAab((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ)) {
            return budget;
        }

        node.seen(walk);

        if (subdivides(node, camera)) {
            budget = request(node, budget);

            if (node.occupancy() != OccupancyMask.EMPTY && node.childrenReady()) {
                descend(node);
                return budget;
            }
        }

        draw(node);
        return budget;
    }

    private boolean subdivides(TreeNode node, CameraFrame camera) {
        if (node.level() <= extent.lowestLevel()) {
            return false;
        }

        double distance = Math.sqrt(axisDistanceSquared(minX, maxX)
                + axisDistanceSquared(minY, maxY)
                + axisDistanceSquared(minZ, maxZ));
        if (distance < INSIDE_DISTANCE) {
            return true;
        }

        double projected = DetailLevel.blocksPerCell(node.level()) * camera.pixelsPerBlock() / distance;
        return projected > camera.subdivisionPixels();
    }

    private int request(TreeNode node, int budget) {
        int missing = node.missingOctants();

        while (missing != 0 && budget > 0) {
            int octant = Integer.numberOfTrailingZeros(missing);
            TreeNode child = nodes.child(node, octant);
            if (child == null) {
                return 0;
            }

            requested.add(child);
            budget--;
            missing &= missing - 1;
        }

        return budget;
    }

    private void descend(TreeNode node) {
        int occupied = node.occupancy();

        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            if (OccupancyMask.isSet(occupied, octant)) {
                next.add(node.child(octant));
            }
        }
    }

    private void draw(TreeNode node) {
        CellMesh mesh = node.mesh();
        if (mesh != null && !mesh.isEmpty()) {
            drawn.add(mesh);
        }
    }

    private void box(TreeNode node, CameraFrame camera) {
        CellFrame frame = extent.frame();
        long key = node.key();
        int level = node.level();
        int side = DetailLevel.blocksPerCell(level);

        minX = frame.originBlockX(CellKey.x(key), level) - camera.eyeX();
        minY = frame.originBlockY(CellKey.y(key), level) - camera.eyeY();
        minZ = frame.originBlockZ(CellKey.z(key), level) - camera.eyeZ();
        maxX = minX + side;
        maxY = minY + side;
        maxZ = minZ + side;
    }

    private static double axisDistanceSquared(double min, double max) {
        double outside = Math.max(min, Math.max(0.0, -max));
        return outside * outside;
    }
}
