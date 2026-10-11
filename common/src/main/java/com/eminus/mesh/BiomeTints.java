package com.eminus.mesh;

public interface BiomeTints {
    int NO_COLOUR = -1;

    boolean positional(int biomeId);

    int constant(int row);

    int colour(int row, int biomeId, int blockX, int blockZ);
}
