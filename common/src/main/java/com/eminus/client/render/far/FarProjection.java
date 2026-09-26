package com.eminus.client.render.far;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class FarProjection {
    public static final float FAR = 48_000.0F;

    private static final double HALF = 0.5;

    public Matrix4f viewProjection(float near, float fov, Matrix4fc fold, Matrix4fc rotation, float width,
            float height, Matrix4f target) {
        return target.setPerspective((float) Math.toRadians(fov), width / height, near, FAR).mul(fold).mul(rotation);
    }

    public static Matrix4f gameViewProjection(Matrix4fc levelProjection, Matrix4fc rotation, Matrix4f target) {
        return target.set(levelProjection).mul(rotation);
    }

    public static Matrix4f reproject(Matrix4fc gameViewProjection, Matrix4fc farViewProjection, Matrix4f farInverse,
            Matrix4f target) {
        farViewProjection.invert(farInverse);
        return gameViewProjection.mul(farInverse, target);
    }

    public static float focalPixels(float fovDegrees, float height) {
        return (float) (height * HALF / Math.tan(Math.toRadians(fovDegrees) * HALF));
    }
}
