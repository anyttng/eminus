package com.eminus.render.far.client;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class LevelProjection {
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f fold = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();

    public void capture(Matrix4fc levelProjection, Matrix4fc cameraProjection) {
        projection.set(levelProjection);
        cameraProjection.invert(inverse).mul(levelProjection, fold);
    }

    public Matrix4fc projection() {
        return projection;
    }

    public Matrix4fc fold() {
        return fold;
    }
}
