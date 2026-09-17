package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;
import com.eminus.mesh.CellMesh;
import com.eminus.settings.FarDistance;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import org.joml.FrustumIntersection;

final class TreeTraversal {
    private static final Comparator<Candidate> LARGEST_FIRST =
            (first, second) -> Float.compare(second.size(), first.size());

    private final NodeTable nodes;
    private final TreeExtent extent;
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final List<TreeNode> current = new ArrayList<>();
    private final List<TreeNode> next = new ArrayList<>();
    private final List<CellMesh> drawn = new ArrayList<>();
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<TreeNode> requested = new ArrayList<>();
    private final FloatArrayList requestedPriorities = new FloatArrayList();

    private boolean starved;
    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;

    private record Candidate(TreeNode node, float size) {
    }

    TreeTraversal(NodeTable nodes, TreeExtent extent) {
        this.nodes = nodes;
        this.extent = extent;
    }

    RenderList walk(Collection<TreeNode> roots, CameraFrame camera, int budget, long walk) {
        frustum.set(camera.viewProjection());
        current.clear();
        current.addAll(roots);
        drawn.clear();
        candidates.clear();
        requested.clear();
        requestedPriorities.clear();
        starved = false;
        double farBlocks = (double) camera.farCells() * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL;

        while (!current.isEmpty()) {
            next.clear();

            for (TreeNode node : current) {
                visit(node, camera, farBlocks, walk);
            }

            current.clear();
            current.addAll(next);
        }

        request(budget);
        return new RenderList(List.copyOf(drawn));
    }

    // Reused by the next walk; consumed before it.
    List<TreeNode> requested() {
        return requested;
    }

    float requestedPriority(int index) {
        return requestedPriorities.getFloat(index);
    }

    boolean starved() {
        return starved;
    }

    private void visit(TreeNode node, CameraFrame camera, double farBlocks, long walk) {
        box(node, camera);

        double horizontal = Math.sqrt(ProjectedSize.axisDistanceSquared(minX, maxX)
                + ProjectedSize.axisDistanceSquared(minZ, maxZ));
        if (horizontal > farBlocks) {
            return;
        }

        if (!frustum.testAab((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ)) {
            return;
        }

        node.seen(walk);

        float size = size(node, camera);
        if (node.level() > extent.lowestLevel() && size > camera.subdivisionPixels()) {
            if (node.missingOctants() != OccupancyMask.EMPTY) {
                candidates.add(new Candidate(node, size));
            }

            if (node.occupancy() != OccupancyMask.EMPTY && node.childrenReady()) {
                node.markDescended();
                descend(node);
                return;
            }
        }

        draw(node);
    }

    private float size(TreeNode node, CameraFrame camera) {
        double distance = Math.sqrt(ProjectedSize.axisDistanceSquared(minX, maxX)
                + ProjectedSize.axisDistanceSquared(minY, maxY)
                + ProjectedSize.axisDistanceSquared(minZ, maxZ));
        return ProjectedSize.of(node.level(), distance, camera);
    }

    private void request(int budget) {
        candidates.sort(LARGEST_FIRST);

        for (Candidate candidate : candidates) {
            int missing = candidate.node().missingOctants();

            while (missing != 0 && budget > 0) {
                TreeNode child = nodes.child(candidate.node(), Integer.numberOfTrailingZeros(missing));
                if (child == null) {
                    starved = true;
                    return;
                }

                requested.add(child);
                requestedPriorities.add(candidate.size());
                budget--;
                missing &= missing - 1;
            }

            if (missing != 0) {
                starved = true;
                return;
            }
        }
    }

    private void descend(TreeNode node) {
        int occupied = node.occupancy();

        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            TreeNode child = node.child(octant);
            if (OccupancyMask.isSet(occupied, octant) && child != null) {
                next.add(child);
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
}
