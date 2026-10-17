package com.eminus.mesh;

public interface BiomeTints {
    int NO_COLOR = -1;

    boolean positional(int biomeId);

    int constant(int row);

    int color(int row, int biomeId, int blockX, int blockZ);
}
