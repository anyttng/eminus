package com.eminus.render.far.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.Projection;

import org.joml.Matrix4f;

public final class FarProjection {
    public static final float NEAR = 16.0F;
    public static final float FAR = 48_000.0F;

    private final Projection projection = new Projection();
    private final Matrix4f rotation = new Matrix4f();

    public Matrix4f viewProjection(Camera camera, float width, float height, Matrix4f target) {
        projection.setupPerspective(NEAR, FAR, camera.getFov(), width, height);
        projection.getMatrix(target);
        return target.mul(camera.getViewRotationMatrix(rotation));
    }
}
