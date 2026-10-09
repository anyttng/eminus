package com.eminus.render.tree;

import com.eminus.cell.DetailLevel;

final class ProjectedSize {
    static final float UNKNOWN = 0.0F;
    static final float CONTAINS_CAMERA = Float.MAX_VALUE;

    private static final double INSIDE_DISTANCE = 1.0;

    static float of(CellBox box, float pixelsPerBlock) {
        double distance = box.distance();
        if (distance < INSIDE_DISTANCE) {
            return CONTAINS_CAMERA;
        }

        return (float) (DetailLevel.blocksPerCell(box.level()) * pixelsPerBlock / distance);
    }

    static float outOfView(float size) {
        return -1.0F / size;
    }

    private ProjectedSize() {
    }
}
