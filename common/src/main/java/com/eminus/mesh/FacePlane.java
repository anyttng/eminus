package com.eminus.mesh;

import java.util.Arrays;

import com.eminus.cell.DetailLevel;

public final class FacePlane {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;

    private final long[] data = new long[SIDE * SIDE];
    private final int[] occlusion = new int[SIDE * SIDE];
    private final int[] rows = new int[SIDE];

    private int count;

    public void clear() {
        Arrays.fill(rows, 0);
        count = 0;
    }

    public void set(int u, int v, long value, int word) {
        data[v * SIDE + u] = value;
        occlusion[v * SIDE + u] = word;
        rows[v] |= 1 << u;
        count++;
    }

    public long data(int u, int v) {
        return data[v * SIDE + u];
    }

    public int occlusion(int u, int v) {
        return occlusion[v * SIDE + u];
    }

    public int row(int v) {
        return rows[v];
    }

    public boolean isEmpty() {
        return count == 0;
    }
}
