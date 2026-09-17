package com.eminus.render.tree;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

final class ProjectedSize {
    static final float UNKNOWN = 0.0F;
    static final float CONTAINS_CAMERA = Float.MAX_VALUE;

    private static final double INSIDE_DISTANCE = 1.0;

    static float of(CellFrame frame, long key, CameraFrame camera) {
        int level = CellKey.level(key);
        int side = DetailLevel.blocksPerCell(level);
        double minX = frame.originBlockX(CellKey.x(key), level) - camera.eyeX();
        double minY = frame.originBlockY(CellKey.y(key), level) - camera.eyeY();
        double minZ = frame.originBlockZ(CellKey.z(key), level) - camera.eyeZ();

        return of(level, Math.sqrt(axisDistanceSquared(minX, minX + side)
                + axisDistanceSquared(minY, minY + side)
                + axisDistanceSquared(minZ, minZ + side)), camera);
    }

    static float of(int level, double distance, CameraFrame camera) {
        if (distance < INSIDE_DISTANCE) {
            return CONTAINS_CAMERA;
        }

        return (float) (DetailLevel.blocksPerCell(level) * camera.pixelsPerBlock() / distance);
    }

    static double axisDistanceSquared(double min, double max) {
        double outside = Math.max(min, Math.max(0.0, -max));
        return outside * outside;
    }

    private ProjectedSize() {
    }
}
