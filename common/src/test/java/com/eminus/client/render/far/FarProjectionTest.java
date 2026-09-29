package com.eminus.client.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class FarProjectionTest {
    private static final float FOV_DEGREES = 70.0F;
    private static final float ASPECT = 16.0F / 9.0F;
    private static final float MARGIN = (float) Math.toRadians(10.0);
    private static final float NEAR = 16.0F;
    private static final float FAR = 48_000.0F;
    private static final float DISTANCE = 1_000.0F;
    private static final float JUST_INSIDE = 0.999F;
    private static final float DELTA = 1.0e-4F;
    private static final int[] SIDES = {-1, 0, 1};

    @Test
    void noMarginKeepsTheViewTheGameDraws() {
        assertEquals(FOV_DEGREES, FarProjection.widenedFov(FOV_DEGREES, ASPECT, 0.0F), DELTA);
        assertEquals(ASPECT, FarProjection.widenedAspect(FOV_DEGREES, ASPECT, 0.0F), DELTA);
    }

    @Test
    void theEdgeOfAViewTurnedByTheMarginIsInsideTheWidenedFrustum() {
        FrustumIntersection widened = frustum(FarProjection.widenedFov(FOV_DEGREES, ASPECT, MARGIN),
                FarProjection.widenedAspect(FOV_DEGREES, ASPECT, MARGIN));
        FrustumIntersection narrow = frustum(FOV_DEGREES, ASPECT);
        Vector3f[] axes = {new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), new Vector3f(1, 1, 0).normalize()};
        boolean narrowMissedOne = false;

        for (Vector3f axis : axes) {
            Quaternionf turn = new Quaternionf().rotateAxis(MARGIN, axis);
            for (int sideX : SIDES) {
                for (int sideY : SIDES) {
                    Vector3f edge = turn.transform(edge(sideX, sideY));
                    assertTrue(widened.testPoint(edge), "axis " + axis + " edge " + sideX + "," + sideY);
                    narrowMissedOne |= !narrow.testPoint(edge);
                }
            }
        }

        assertTrue(narrowMissedOne);
    }

    @Test
    void theWidenedHalfAngleStopsShortOfARightAngle() {
        assertEquals(170.0F, FarProjection.widenedFov(FOV_DEGREES, ASPECT, (float) Math.toRadians(80.0)), DELTA);
    }

    private static Vector3f edge(int sideX, int sideY) {
        float tanVertical = (float) Math.tan(Math.toRadians(FOV_DEGREES) / 2.0);
        float tanHorizontal = tanVertical * ASPECT;
        return new Vector3f(sideX * tanHorizontal * JUST_INSIDE, sideY * tanVertical * JUST_INSIDE, -1.0F)
                .normalize(DISTANCE);
    }

    private static FrustumIntersection frustum(float fovDegrees, float aspect) {
        return new FrustumIntersection(
                new Matrix4f().perspective((float) Math.toRadians(fovDegrees), aspect, NEAR, FAR));
    }
}
