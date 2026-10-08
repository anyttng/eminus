package com.eminus.render.tree;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

import org.joml.FrustumIntersection;

final class CellBox {
    private int level;
    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;

    CellBox set(CellFrame frame, long key, CameraFrame camera) {
        level = CellKey.level(key);
        int side = DetailLevel.blocksPerCell(level);

        minX = CellFrame.originXOf(key) - camera.eyeX();
        minY = frame.originYOf(key) - camera.eyeY();
        minZ = CellFrame.originZOf(key) - camera.eyeZ();
        maxX = minX + side;
        maxY = minY + side;
        maxZ = minZ + side;
        return this;
    }

    int level() {
        return level;
    }

    double distance() {
        return Math.sqrt(axisDistanceSquared(minX, maxX) + axisDistanceSquared(minY, maxY)
                + axisDistanceSquared(minZ, maxZ));
    }

    double horizontalDistance() {
        return Math.sqrt(axisDistanceSquared(minX, maxX) + axisDistanceSquared(minZ, maxZ));
    }

    boolean meets(FrustumIntersection frustum) {
        return frustum.testAab((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ);
    }

    private static double axisDistanceSquared(double min, double max) {
        double outside = Math.max(min, Math.max(0.0, -max));
        return outside * outside;
    }
}
