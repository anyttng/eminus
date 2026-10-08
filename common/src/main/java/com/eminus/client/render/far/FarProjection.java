package com.eminus.client.render.far;

import net.minecraft.client.renderer.Projection;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class FarProjection {
    public static final float FAR = 48_000.0F;

    private static final double HALF = 0.5;
    private static final double WIDEST_HALF_ANGLE = Math.toRadians(85.0);
    private static final float UNIT_HEIGHT = 1.0F;
    private static final double UNIT_DEPTH = 1.0;
    private static final double MAX_SINE = 1.0;

    private final Projection projection = new Projection();
    private final Projection walkProjection = new Projection();

    public Matrix4f viewProjection(float near, float fov, Matrix4fc fold, Matrix4fc rotation, float width,
            float height, Matrix4f target) {
        projection.setupPerspective(near, FAR, fov, width, height);
        projection.getMatrix(target);
        return target.mul(fold).mul(rotation);
    }

    public Matrix4f walkViewProjection(float near, float fov, float turnMargin, Matrix4fc fold, Matrix4fc rotation,
            float width, float height, Matrix4f target) {
        float aspect = width / height;
        walkProjection.setupPerspective(near, FAR, widenedFov(fov, aspect, turnMargin),
                widenedAspect(fov, aspect, turnMargin), UNIT_HEIGHT);
        walkProjection.getMatrix(target);
        return target.mul(fold).mul(rotation);
    }

    static float widenedFov(float fovDegrees, float aspect, float turnMargin) {
        double halfVertical = halfVertical(fovDegrees);
        double corner = cornerLength(halfVertical, aspect);
        return (float) Math.toDegrees(widened(halfVertical, corner, turnMargin) / HALF);
    }

    static float widenedAspect(float fovDegrees, float aspect, float turnMargin) {
        double halfVertical = halfVertical(fovDegrees);
        double halfHorizontal = Math.atan(Math.tan(halfVertical) * aspect);
        double corner = cornerLength(halfVertical, aspect);
        return (float) (Math.tan(widened(halfHorizontal, corner, turnMargin))
                / Math.tan(widened(halfVertical, corner, turnMargin)));
    }

    private static double halfVertical(float fovDegrees) {
        return Math.toRadians(fovDegrees) * HALF;
    }

    private static double cornerLength(double halfVertical, float aspect) {
        double tanVertical = Math.tan(halfVertical);
        double tanHorizontal = tanVertical * aspect;
        return Math.sqrt(UNIT_DEPTH + tanVertical * tanVertical + tanHorizontal * tanHorizontal);
    }

    private static double widened(double halfAngle, double cornerLength, float turnMargin) {
        double outward = Math.asin(Math.min(MAX_SINE, cornerLength * Math.sin(turnMargin) * Math.cos(halfAngle)));
        return Math.min(halfAngle + outward, Math.max(halfAngle, WIDEST_HALF_ANGLE));
    }

    public static Matrix4f gameViewProjection(Matrix4fc levelProjection, Matrix4fc rotation, Matrix4f target) {
        return target.set(levelProjection).mul(rotation);
    }

    public static Matrix4f reproject(Matrix4fc gameViewProjection, Matrix4fc farViewProjection, Matrix4f farInverse,
            Matrix4f target) {
        farViewProjection.invert(farInverse);
        return gameViewProjection.mul(farInverse, target);
    }

    public static Matrix4f gameToFar(Matrix4fc farViewProjection, Matrix4fc gameViewProjection, Matrix4f gameInverse,
            Matrix4f target) {
        gameViewProjection.invert(gameInverse);
        return farViewProjection.mul(gameInverse, target);
    }

    public static float focalPixels(float fovDegrees, float height) {
        return (float) (height * HALF / Math.tan(Math.toRadians(fovDegrees) * HALF));
    }
}
