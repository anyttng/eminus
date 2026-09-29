package com.eminus.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eminus.render.tree.CameraFrame;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.junit.jupiter.api.Test;

class TurnMarginTest {
    private static final float STEP = (float) Math.toRadians(3.0);
    private static final float DELTA = 1.0e-5F;
    private static final float PIXELS_PER_BLOCK = 1.0F;
    private static final int FAR_CELLS = 1;
    private static final int THRESHOLD_PIXELS = 64;

    private final TurnMargin margin = new TurnMargin();

    @Test
    void nothingDrawnYetWidensNothing() {
        assertEquals(TurnMargin.NO_TURN, margin.frame(yaw(5)));
    }

    @Test
    void theMarginIsHowFarTheViewTurnedSinceTheDrawnListWasWalked() {
        margin.drawn(walked(1, 0));

        assertEquals(2 * STEP, margin.frame(yaw(2)), DELTA);
    }

    @Test
    void aListReplacedMidTurnKeepsItsWholeTurnInTheMargin() {
        margin.drawn(walked(1, 0));
        margin.frame(yaw(2));
        margin.frame(yaw(3));
        margin.frame(yaw(4));
        margin.drawn(walked(3, 2));

        assertEquals(4 * STEP, margin.frame(yaw(5)), DELTA);
    }

    @Test
    void aListWalkedWhereTheCameraStoppedWidensNothing() {
        margin.drawn(walked(1, 0));
        margin.frame(yaw(2));
        margin.frame(yaw(4));
        margin.drawn(walked(4, 4));

        assertEquals(TurnMargin.NO_TURN, margin.frame(yaw(4)));
    }

    @Test
    void theAngleIsTheRotationBetweenTwoViewsWhicheverSignTheQuaternionCarries() {
        Quaternionf turned = new Quaternionf().rotateY((float) Math.toRadians(30.0)).rotateX(STEP);
        Quaternionf flipped = new Quaternionf(-turned.x, -turned.y, -turned.z, -turned.w);

        assertEquals(TurnMargin.NO_TURN, TurnMargin.angle(turned, flipped), DELTA);
        assertEquals(Math.toRadians(30.0), TurnMargin.angle(new Quaternionf(), new Quaternionf()
                .rotateY((float) Math.toRadians(30.0))), DELTA);
    }

    private static Quaternionfc yaw(int steps) {
        return new Quaternionf().rotateY(steps * STEP);
    }

    private static CameraFrame walked(long frame, int steps) {
        return new CameraFrame(0.0, 0.0, 0.0, new Matrix4f(), PIXELS_PER_BLOCK, PIXELS_PER_BLOCK, FAR_CELLS,
                THRESHOLD_PIXELS, false, frame, yaw(steps), TurnMargin.NO_TURN);
    }
}
