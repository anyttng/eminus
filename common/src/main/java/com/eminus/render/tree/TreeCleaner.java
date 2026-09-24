package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.OccupancyMask;
import com.eminus.settings.FarDistance;

final class TreeCleaner {
    static final int PRESSURE_EVICTIONS = 64;

    private static final Comparator<Candidate> OLDEST_FIRST = Comparator.comparingLong(Candidate::lastSeen)
            .thenComparingDouble(Candidate::size)
            .thenComparingLong(Candidate::key);
    private static final Comparator<Candidate> SMALLEST_FIRST = Comparator.comparingDouble(Candidate::size)
            .thenComparingLong(Candidate::key);

    private final List<Candidate> unwanted = new ArrayList<>();
    private final List<Candidate> outOfView = new ArrayList<>();
    private final List<TreeNode> picked = new ArrayList<>();

    private int unwantedPicked;

    private record Candidate(TreeNode parent, long lastSeen, float size) {
        long key() {
            return parent.key();
        }
    }

    List<TreeNode> pick(Collection<TreeNode> all, CameraFrame camera, CellFrame frame, long walk) {
        picked.clear();
        unwantedPicked = 0;
        if (!camera.pressure()) {
            return List.of();
        }

        unwanted.clear();
        outOfView.clear();
        double farBlocks = (double) camera.farCells() * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL;
        for (TreeNode node : all) {
            classify(node, camera, frame, walk, farBlocks);
        }

        unwanted.sort(OLDEST_FIRST);
        outOfView.sort(SMALLEST_FIRST);

        take(unwanted);
        unwantedPicked = picked.size();
        take(outOfView);

        return List.copyOf(picked);
    }

    int unwantedPicked() {
        return unwantedPicked;
    }

    private void classify(TreeNode parent, CameraFrame camera, CellFrame frame, long walk, double farBlocks) {
        long lastSeen = 0;
        boolean meshed = false;

        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            TreeNode child = parent.child(octant);
            if (child == null) {
                continue;
            }

            if (child.lastSeen() >= walk) {
                return;
            }

            lastSeen = Math.max(lastSeen, child.lastSeen());
            meshed |= child.mesh() != null;
        }

        if (!meshed) {
            return;
        }

        float size = ProjectedSize.of(frame, parent.key(), camera);
        Candidate candidate = new Candidate(parent, lastSeen, size);
        if (size > camera.subdivisionPixels()
                && ProjectedSize.horizontalDistance(frame, parent.key(), camera) <= farBlocks) {
            outOfView.add(candidate);
        } else {
            unwanted.add(candidate);
        }
    }

    private int take(List<Candidate> candidates) {
        int taken = 0;

        while (taken < candidates.size() && picked.size() < PRESSURE_EVICTIONS) {
            TreeNode parent = candidates.get(taken++).parent();
            for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
                TreeNode child = parent.child(octant);
                if (child != null) {
                    picked.add(child);
                }
            }
        }

        return taken;
    }
}
