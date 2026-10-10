package com.eminus.render.far;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ShadowCasterVolumeTest {
    private static final float RIGHT_ANGLE = (float) Math.toRadians(90.0);
    private static final float NEAR = 0.05F;
    private static final float FAR = 48_000.0F;
    private static final float HALF = 0.5F;
    private static final Vector3f DOWN = new Vector3f(0.0F, -1.0F, 0.0F);
    private static final Vector3f TOWARDS_THE_CAMERA = new Vector3f(0.0F, 0.0F, 1.0F);

    private final Matrix4f camera = new Matrix4f().perspective(RIGHT_ANGLE, 1.0F, NEAR, FAR);

    @Test
    void aBoxAboveThePointsInViewCastsDownIntoView() {
        assertTrue(casts(DOWN, 0.0F, 100.0F, -50.0F));
    }

    @Test
    void aBoxInsideTheViewCasts() {
        assertTrue(casts(DOWN, 0.0F, 0.0F, -50.0F));
    }

    @Test
    void aBoxBehindTheCameraCastsNothingIntoViewUnderALightFromAbove() {
        assertFalse(casts(DOWN, 0.0F, 100.0F, 50.0F));
    }

    @Test
    void aBoxBesideTheViewCastsNothingIntoViewUnderALightFromAbove() {
        assertFalse(casts(DOWN, 200.0F, 100.0F, -50.0F));
    }

    @Test
    void aBoxTwoBlocksBesideTheViewStillCastsForALookupThatReachesPastItsReceiver() {
        assertTrue(casts(DOWN, 52.0F, 100.0F, -50.0F));
    }

    @Test
    void aBoxBelowTheViewCastsOnlyFurtherDown() {
        assertFalse(casts(DOWN, 0.0F, -200.0F, -50.0F));
    }

    @Test
    void aBoxBehindTheCameraCastsNothingWhenTheLightTravelsTowardsTheCamera() {
        assertFalse(casts(TOWARDS_THE_CAMERA, 0.0F, 0.0F, 50.0F));
    }

    @Test
    void aBoxAheadOfTheCameraCastsWhenTheLightTravelsTowardsTheCamera() {
        assertTrue(casts(TOWARDS_THE_CAMERA, 0.0F, 0.0F, -100.0F));
    }

    private boolean casts(Vector3f lightTravel, float x, float y, float z) {
        return new ShadowCasterVolume().set(camera, lightTravel)
                .testAab(x - HALF, y - HALF, z - HALF, x + HALF, y + HALF, z + HALF);
    }
}
