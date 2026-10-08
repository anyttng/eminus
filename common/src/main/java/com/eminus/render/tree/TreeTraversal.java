package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;
import com.eminus.mesh.MeshSummary;
import com.eminus.settings.FarDistance;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.Direction;

import org.joml.FrustumIntersection;

final class TreeTraversal {
    private static final Direction[] FACES = Direction.values();
    private static final Comparator<Candidate> LARGEST_FIRST =
            (first, second) -> Float.compare(second.size(), first.size());
    private static final int UNSERVED = -1;

    private final NodeTable nodes;
    private final TreeExtent extent;
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final CellBox box = new CellBox();
    private final List<TreeNode> current = new ArrayList<>();
    private final List<TreeNode> next = new ArrayList<>();
    private final List<MeshSummary> drawn = new ArrayList<>();
    private final LongOpenHashSet drawnKeys = new LongOpenHashSet();
    private final LongOpenHashSet descendedKeys = new LongOpenHashSet();
    private final List<MeshSummary> outOfViewDrawn = new ArrayList<>();
    private final LongOpenHashSet outOfViewDrawnKeys = new LongOpenHashSet();
    private final LongOpenHashSet outOfViewDescendedKeys = new LongOpenHashSet();
    private final List<TreeNode> outside = new ArrayList<>();
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<TreeNode> requested = new ArrayList<>();
    private final FloatArrayList requestedPriorities = new FloatArrayList();
    private final List<TreeNode> outOfViewRequested = new ArrayList<>();
    private final FloatArrayList outOfViewSizes = new FloatArrayList();

    private boolean starved;

    private record Candidate(TreeNode node, float size) {
    }

    TreeTraversal(NodeTable nodes, TreeExtent extent) {
        this.nodes = nodes;
        this.extent = extent;
    }

    RenderList walk(Collection<TreeNode> roots, CameraFrame camera, int budget, int outOfViewBudget, long walk) {
        frustum.set(camera.viewProjection());
        current.clear();
        current.addAll(roots);
        drawn.clear();
        drawnKeys.clear();
        descendedKeys.clear();
        outside.clear();
        candidates.clear();
        requested.clear();
        requestedPriorities.clear();
        outOfViewRequested.clear();
        outOfViewSizes.clear();
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

        if (!camera.pressure()) {
            starved = hand(candidates, budget, requested, requestedPriorities) == UNSERVED;
            int outOfViewLeft = outOfViewBudget - requested.size();
            if (!starved && outOfViewLeft > 0) {
                requestOutOfView(camera, farBlocks, outOfViewLeft);
            }
        }

        return list(camera);
    }

    RenderList list(CameraFrame camera) {
        outOfViewDrawn.clear();
        outOfViewDrawnKeys.clear();
        outOfViewDescendedKeys.clear();
        double farBlocks = (double) camera.farCells() * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL;
        current.clear();
        current.addAll(outside);

        while (!current.isEmpty()) {
            next.clear();

            for (TreeNode node : current) {
                listOutOfView(node, camera, farBlocks);
            }

            current.clear();
            current.addAll(next);
        }

        List<MeshSummary> meshes = new ArrayList<>(drawn.size() + outOfViewDrawn.size());
        meshes.addAll(drawn);
        meshes.addAll(outOfViewDrawn);
        return new RenderList(List.copyOf(meshes), borders(meshes), camera);
    }

    private Long2IntMap borders(List<MeshSummary> meshes) {
        Long2IntOpenHashMap borders = new Long2IntOpenHashMap();
        for (MeshSummary mesh : meshes) {
            int faces = borderFaces(mesh.key());
            if (faces != RenderList.NO_BORDER_FACES) {
                borders.put(mesh.key(), faces);
            }
        }

        return borders;
    }

    private int borderFaces(long key) {
        int faces = RenderList.NO_BORDER_FACES;
        for (Direction face : FACES) {
            long neighbour = CellKey.neighbour(key, face);
            if (!drawn(neighbour) && (descended(neighbour) || ancestorDrawn(neighbour))) {
                faces |= 1 << face.ordinal();
            }
        }

        return faces;
    }

    private boolean ancestorDrawn(long key) {
        long ancestor = key;
        for (int level = CellKey.level(key) + 1; level <= DetailLevel.MAX; level++) {
            ancestor = CellKey.parent(ancestor);
            if (drawn(ancestor)) {
                return true;
            }
        }

        return false;
    }

    private boolean drawn(long key) {
        return drawnKeys.contains(key) || outOfViewDrawnKeys.contains(key);
    }

    private boolean descended(long key) {
        return descendedKeys.contains(key) || outOfViewDescendedKeys.contains(key);
    }

    // Reused by the next walk; consumed before it.
    List<TreeNode> requested() {
        return requested;
    }

    float requestedPriority(int index) {
        return requestedPriorities.getFloat(index);
    }

    // Reused by the next walk; consumed before it.
    List<TreeNode> outOfViewRequested() {
        return outOfViewRequested;
    }

    float outOfViewSize(int index) {
        return outOfViewSizes.getFloat(index);
    }

    boolean starved() {
        return starved;
    }


    private void visit(TreeNode node, CameraFrame camera, double farBlocks, long walk) {
        box.set(extent.frame(), node.key(), camera);

        if (box.horizontalDistance() > farBlocks) {
            return;
        }

        if (!box.meets(frustum)) {
            outside.add(node);
            return;
        }

        node.seen(walk);

        float size = ProjectedSize.of(box, camera.inViewPixelsPerBlock());
        if (node.level() > extent.lowestLevel() && size > camera.subdivisionPixels()) {
            if (node.missingOctants() != OccupancyMask.EMPTY) {
                candidates.add(new Candidate(node, size));
            }

            keepChildren(node, walk);
            if (node.occupancy() != OccupancyMask.EMPTY && node.childrenReady()) {
                node.markDescended();
                descendedKeys.add(node.key());
                descend(node);
                return;
            }
        }

        draw(node);
    }

    private void listOutOfView(TreeNode node, CameraFrame camera, double farBlocks) {
        box.set(extent.frame(), node.key(), camera);

        if (box.horizontalDistance() > farBlocks) {
            return;
        }

        if (node.level() > extent.lowestLevel() && ProjectedSize.of(box, camera.pixelsPerBlock()) > camera.subdivisionPixels()
                && node.occupancy() != OccupancyMask.EMPTY && node.childrenReady()) {
            node.markDescended();
            outOfViewDescendedKeys.add(node.key());
            descend(node);
            return;
        }

        outOfViewDrawnKeys.add(node.key());
        MeshSummary mesh = node.mesh();
        if (mesh != null && !mesh.isEmpty()) {
            outOfViewDrawn.add(mesh);
        }
    }

    private static void keepChildren(TreeNode node, long walk) {
        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            TreeNode child = node.child(octant);
            if (child != null) {
                child.seen(walk);
            }
        }
    }

    private void requestOutOfView(CameraFrame camera, double farBlocks, int budget) {
        candidates.clear();
        current.clear();
        current.addAll(outside);

        while (!current.isEmpty()) {
            next.clear();

            for (TreeNode node : current) {
                collectOutOfView(node, camera, farBlocks);
            }

            current.clear();
            current.addAll(next);
        }

        hand(candidates, budget, outOfViewRequested, outOfViewSizes);
    }

    private void collectOutOfView(TreeNode node, CameraFrame camera, double farBlocks) {
        box.set(extent.frame(), node.key(), camera);

        if (box.horizontalDistance() > farBlocks) {
            return;
        }

        float size = ProjectedSize.of(box, camera.pixelsPerBlock());
        if (node.level() <= extent.lowestLevel() || size <= camera.subdivisionPixels()) {
            return;
        }

        if (node.missingOctants() != OccupancyMask.EMPTY) {
            candidates.add(new Candidate(node, size));
        }

        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            TreeNode child = node.child(octant);
            if (child != null) {
                next.add(child);
            }
        }
    }

    private int hand(List<Candidate> from, int budget, List<TreeNode> into, FloatArrayList sizes) {
        from.sort(LARGEST_FIRST);
        int left = budget;

        for (Candidate candidate : from) {
            int missing = candidate.node().missingOctants();

            while (missing != 0 && left > 0) {
                TreeNode child = nodes.child(candidate.node(), Integer.numberOfTrailingZeros(missing));
                if (child == null) {
                    return UNSERVED;
                }

                into.add(child);
                sizes.add(candidate.size());
                left--;
                missing &= missing - 1;
            }

            if (missing != 0) {
                return UNSERVED;
            }
        }

        return left;
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
        drawnKeys.add(node.key());
        MeshSummary mesh = node.mesh();
        if (mesh != null && !mesh.isEmpty()) {
            drawn.add(mesh);
        }
    }
}
