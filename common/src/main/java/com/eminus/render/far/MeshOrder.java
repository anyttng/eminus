package com.eminus.render.far;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.mesh.MeshSummary;
import com.eminus.render.tree.RenderList;

public final class MeshOrder {
    private static final int INDEX_BITS = 32;
    private static final long INDEX_MASK = 0xFFFF_FFFFL;
    private static final float HALF = 0.5F;

    private final List<MeshSummary> nearest = new ArrayList<>();
    private final List<MeshSummary> translucent = new ArrayList<>();

    private List<MeshSummary> seen = List.of();
    private long[] distances = new long[0];
    private long cameraCell;
    private int sorts;

    public List<MeshSummary> meshes() {
        return nearest;
    }

    public List<MeshSummary> translucent() {
        return translucent;
    }

    public int takeSorts() {
        int taken = sorts;
        sorts = 0;
        return taken;
    }

    public void update(RenderList list, CellFrame frame, double cameraX, double cameraY, double cameraZ) {
        List<MeshSummary> meshes = list.meshes();
        long cell = frame.keyAt(DetailLevel.MIN, floor(cameraX), floor(cameraY), floor(cameraZ));
        if (cell == cameraCell && sameMembers(meshes)) {
            return;
        }

        cameraCell = cell;
        seen = meshes;
        sort(meshes, frame, cameraX, cameraY, cameraZ);
        sorts++;
    }

    private boolean sameMembers(List<MeshSummary> meshes) {
        if (meshes == seen) {
            return true;
        }

        if (meshes.size() != seen.size()) {
            return false;
        }

        for (int index = 0; index < meshes.size(); index++) {
            if (meshes.get(index) != seen.get(index)) {
                return false;
            }
        }

        return true;
    }

    private void sort(List<MeshSummary> meshes, CellFrame frame, double cameraX, double cameraY, double cameraZ) {
        int count = meshes.size();
        if (distances.length < count) {
            distances = new long[count];
        }

        for (int index = 0; index < count; index++) {
            float squared = distanceSquared(meshes.get(index).key(), frame, cameraX, cameraY, cameraZ);
            distances[index] = (long) Float.floatToRawIntBits(squared) << INDEX_BITS | index;
        }

        Arrays.sort(distances, 0, count);

        nearest.clear();
        translucent.clear();
        for (int at = 0; at < count; at++) {
            nearest.add(meshes.get((int) (distances[at] & INDEX_MASK)));
        }

        for (int at = count - 1; at >= 0; at--) {
            MeshSummary mesh = nearest.get(at);
            if (mesh.translucentQuads() > 0) {
                translucent.add(mesh);
            }
        }
    }

    private static float distanceSquared(long key, CellFrame frame, double cameraX, double cameraY, double cameraZ) {
        int level = CellKey.level(key);
        float half = DetailLevel.blocksPerCell(level) * HALF;
        double dx = frame.originBlockX(CellKey.x(key), level) + half - cameraX;
        double dy = frame.originBlockY(CellKey.y(key), level) + half - cameraY;
        double dz = frame.originBlockZ(CellKey.z(key), level) + half - cameraZ;

        return (float) (dx * dx + dy * dy + dz * dz);
    }

    private static int floor(double block) {
        return (int) Math.floor(block);
    }
}
