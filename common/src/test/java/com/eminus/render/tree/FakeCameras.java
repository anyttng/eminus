package com.eminus.render.tree;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

final class FakeCameras {
    static final int THRESHOLD_PIXELS = 64;
    static final float CLOSE_PIXELS_PER_BLOCK = 1_000.0F;
    static final float FAR_PIXELS_PER_BLOCK = 0.001F;

    private static final float EVERYWHERE = 1.0e7F;
    private static final float FOV_RADIANS = (float) Math.toRadians(70.0);
    private static final float ASPECT = 1.0F;
    private static final float NEAR = 16.0F;
    private static final float FAR = 48_000.0F;

    private static final Matrix4fc EVERYTHING = new Matrix4f()
            .ortho(-EVERYWHERE, EVERYWHERE, -EVERYWHERE, EVERYWHERE, -EVERYWHERE, EVERYWHERE);

    static CameraFrame everything(double x, double y, double z, int farCells, float pixelsPerBlock) {
        return new CameraFrame(x, y, z, EVERYTHING, pixelsPerBlock, farCells, THRESHOLD_PIXELS, false);
    }

    static CameraFrame looking(double x, double y, double z, float dirX, float dirY, float dirZ, int farCells,
            float pixelsPerBlock) {
        Matrix4f viewProjection = new Matrix4f()
                .perspective(FOV_RADIANS, ASPECT, NEAR, FAR)
                .lookAlong(dirX, dirY, dirZ, 0.0F, 1.0F, 0.0F);
        return new CameraFrame(x, y, z, viewProjection, pixelsPerBlock, farCells, THRESHOLD_PIXELS, false);
    }

    static CameraFrame underPressure(CameraFrame frame) {
        return new CameraFrame(frame.eyeX(), frame.eyeY(), frame.eyeZ(), frame.viewProjection(),
                frame.pixelsPerBlock(), frame.farCells(), frame.subdivisionPixels(), true);
    }

    private FakeCameras() {
    }
}
