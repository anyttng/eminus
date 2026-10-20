package com.eminus.mesh;

import net.minecraft.core.Direction;

final class FaceSide {
    final int edge;

    @SuppressWarnings("NullAway.Init")
    Direction towards;
    long facing;
    int x;
    int y;
    int z;
    int model;
    int drawn;
    boolean face;
    boolean border;

    FaceSide(int edge) {
        this.edge = edge;
    }

    void at(CellVoxels voxels, int atX, int atY, int atZ) {
        x = atX;
        y = atY;
        z = atZ;
        facing = voxels.entry(atX, atY, atZ);
    }
}
