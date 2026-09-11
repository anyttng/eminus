package com.eminus.render.far.client;

import net.minecraft.client.renderer.Projection;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class FarProjection {
    public static final float FAR = 48_000.0F;

    private static final double HALF = 0.5;

    private final Projection projection = new Projection();

    public Matrix4f viewProjection(float near, float fov, Matrix4fc fold, Matrix4fc rotation, float width,
            float height, Matrix4f target) {
        projection.setupPerspective(near, FAR, fov, width, height);
        projection.getMatrix(target);
        return target.mul(fold).mul(rotation);
    }

    public static Matrix4f gameViewProjection(Matrix4fc levelProjection, Matrix4fc rotation, Matrix4f target) {
        return target.set(levelProjection).mul(rotation);
    }

    // The detail metric follows the fov the player set, so a spyglass or a sprint moves no level.
    public static float focalPixels(int fovDegrees, float height) {
        return (float) (height * HALF / Math.tan(Math.toRadians(fovDegrees) * HALF));
    }
}
