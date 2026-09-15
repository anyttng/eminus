package com.eminus.render.far.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.handoff.NearPlane;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class LevelProjectionTest {
    private static final float FOV = (float) Math.toRadians(70.0);
    private static final float ASPECT = 16.0F / 9.0F;
    private static final float NEAR = 0.05F;
    private static final float FAR = 1024.0F;
    private static final float DELTA = 1.0E-4F;

    private final LevelProjection levelProjection = new LevelProjection();
    private final Matrix4f camera = new Matrix4f().setPerspective(FOV, ASPECT, NEAR, FAR);

    @Test
    void theFoldIsTheTransformTheGameAppliedToItsProjection() {
        Matrix4f bob = new Matrix4f().translate(0.1F, -0.2F, 0.0F).rotateZ(0.05F).rotateX(0.03F);

        levelProjection.capture(camera.mul(bob, new Matrix4f()), camera);

        assertMatrix(bob, levelProjection.fold());
    }

    @Test
    void theFoldIsIdentityWhileTheGameBobsNothing() {
        levelProjection.capture(camera, camera);

        assertMatrix(new Matrix4f(), levelProjection.fold());
    }

    @Test
    void theCapturedProjectionIsTheOneTheGameDrewWith() {
        Matrix4f bobbed = camera.mul(new Matrix4f().rotateZ(0.2F), new Matrix4f());

        levelProjection.capture(bobbed, camera);

        assertMatrix(bobbed, levelProjection.projection());
    }

    @Test
    void theFarLayerAndTheGameMoveOnePointTogether() {
        Matrix4f bob = new Matrix4f().translate(0.0F, -0.15F, 0.0F).rotateZ(0.08F);
        Matrix4f rotation = new Matrix4f().rotateY(0.7F).rotateX(0.2F);
        Matrix4f far = new Matrix4f().setPerspective(FOV, ASPECT, NearPlane.BLOCKS, FarProjection.FAR);
        Vector4f point = new Vector4f(120.0F, 30.0F, -4000.0F, 1.0F);

        levelProjection.capture(camera.mul(bob, new Matrix4f()), camera);

        Vector4f farPoint = far.mul(levelProjection.fold(), new Matrix4f()).mul(rotation).transform(point,
                new Vector4f());
        Vector4f gamePoint = FarProjection
                .gameViewProjection(levelProjection.projection(), rotation, new Matrix4f())
                .transform(point, new Vector4f());

        assertTrue(farPoint.w > 0.0F);
        assertEquals(gamePoint.x / gamePoint.w, farPoint.x / farPoint.w, DELTA);
        assertEquals(gamePoint.y / gamePoint.w, farPoint.y / farPoint.w, DELTA);
    }

    @Test
    void theReprojectedFarDepthIsTheGameDepthUnderABobAndAHurtTilt() {
        Matrix4f bob = new Matrix4f().translate(0.0F, -0.15F, 0.0F).rotateZ(0.08F).rotateX(0.03F);
        Matrix4f rotation = new Matrix4f().rotateY(0.7F).rotateX(0.2F);
        Matrix4f gameCamera = reversed(NEAR, FAR);
        Vector4f point = new Vector4f(12.0F, 6.0F, -40.0F, 1.0F);

        levelProjection.capture(gameCamera.mul(bob, new Matrix4f()), gameCamera);

        Matrix4f farViewProjection = reversed(NearPlane.BLOCKS, FarProjection.FAR).mul(levelProjection.fold())
                .mul(rotation);
        Matrix4f gameViewProjection = FarProjection.gameViewProjection(levelProjection.projection(), rotation,
                new Matrix4f());
        Vector4f farNdc = ndc(farViewProjection.transform(point, new Vector4f()));
        Vector4f reprojected = ndc(FarProjection
                .reproject(gameViewProjection, farViewProjection, new Matrix4f(), new Matrix4f())
                .transform(farNdc, new Vector4f()));
        Vector4f game = ndc(gameViewProjection.transform(point, new Vector4f()));

        assertEquals(game.x, reprojected.x, DELTA);
        assertEquals(game.y, reprojected.y, DELTA);
        assertEquals(game.z, reprojected.z, CompositePass.DEPTH_BIAS);
    }

    @Test
    void theReprojectionIsTheSameWithAndWithoutTheBob() {
        Matrix4f bob = new Matrix4f().translate(0.0F, -0.15F, 0.0F).rotateZ(0.08F).rotateX(0.03F);
        Matrix4f rotation = new Matrix4f().rotateY(0.7F).rotateX(0.2F);
        Matrix4f gameCamera = reversed(NEAR, FAR);

        levelProjection.capture(gameCamera.mul(bob, new Matrix4f()), gameCamera);
        Matrix4f bobbed = reprojection(rotation);
        levelProjection.capture(gameCamera, gameCamera);
        Matrix4f still = reprojection(rotation);

        assertMatrix(still, bobbed);
    }

    private Matrix4f reprojection(Matrix4fc rotation) {
        Matrix4f farViewProjection = reversed(NearPlane.BLOCKS, FarProjection.FAR).mul(levelProjection.fold())
                .mul(rotation);
        Matrix4f gameViewProjection = FarProjection.gameViewProjection(levelProjection.projection(), rotation,
                new Matrix4f());
        return FarProjection.reproject(gameViewProjection, farViewProjection, new Matrix4f(), new Matrix4f());
    }

    // The game swaps near and far in Projection.getMatrix; an unswapped matrix tests a depth range it never uses.
    private static Matrix4f reversed(float near, float far) {
        return new Matrix4f().setPerspective(FOV, ASPECT, far, near, false);
    }

    private static Vector4f ndc(Vector4f clip) {
        return new Vector4f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w, 1.0F);
    }

    private static void assertMatrix(Matrix4fc expected, Matrix4fc actual) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                assertEquals(expected.get(column, row), actual.get(column, row), DELTA);
            }
        }
    }
}
