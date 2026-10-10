package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeHorizonTest {
    private static final double EYE = 0.0;
    private static final int FAR_CELLS = 16;
    private static final float PIXELS_PER_BLOCK = 1.0F;
    private static final double HORIZON = 100.0;
    private static final double LIFTED = 110.0;
    private static final double TOLERANCE = 1.0e-9;
    private static final double PAST_RING_STEP = 128.0;
    private static final double SHORT_OF_RING_STEP = 127.0;
    private static final double NEAR_FAR_DISTANCE = 8_000.0;
    private static final int LIFT_FRAMES = 300;
    private static final int LEVEL_ONE = 1;
    private static final int LEVEL_TWO = 2;

    private final TreeHorizon horizon = new TreeHorizon();

    @Test
    void anUnboundedHorizonRefinesEverythingAndIsSettled() {
        assertTrue(horizon.refines(Double.MAX_VALUE));
        assertTrue(horizon.settled());
    }

    @Test
    void aLoweredHorizonRefinesOnlyBelowItAndWaitsForALift() {
        horizon.lower(HORIZON);

        assertTrue(horizon.refines(HORIZON - 1.0));
        assertFalse(horizon.refines(HORIZON));
        assertFalse(horizon.settled());
    }

    @Test
    void aLevelZeroSetAtAThousandBlocksGoesBeforeALevelOneSetAtFourteenHundred() {
        assertEquals(1_000.0, TreeHorizon.key(1_000.0, LEVEL_ONE), TOLERANCE);
        assertEquals(1_400.0 / 1.5, TreeHorizon.key(1_400.0, LEVEL_TWO), TOLERANCE);
        assertTrue(TreeHorizon.key(1_000.0, LEVEL_ONE) > TreeHorizon.key(1_400.0, LEVEL_TWO));
    }

    @Test
    void theHorizonLiftsOneStepAfterTheQuietFramesAndNotBefore() {
        horizon.lower(HORIZON);

        for (int frame = 1; frame < LIFT_FRAMES; frame++) {
            assertFalse(horizon.frame(quiet(EYE), true));
        }

        assertTrue(horizon.frame(quiet(EYE), true));
        assertEquals(LIFTED, horizon.horizon(), TOLERANCE);
    }

    @Test
    void aBusyFrameStartsTheQuietCountAgain() {
        horizon.lower(HORIZON);

        for (int frame = 1; frame < LIFT_FRAMES; frame++) {
            horizon.frame(quiet(EYE), true);
        }
        horizon.frame(quiet(EYE), false);

        assertFalse(horizon.frame(quiet(EYE), true));
        assertEquals(HORIZON, horizon.horizon());
    }

    @Test
    void aLiftThatMeetsPressureLocksUntilTheCameraMovesARingStep() {
        horizon.lower(HORIZON);
        lift();
        horizon.frame(FakeCameras.underPressure(quiet(EYE)), true);
        horizon.lower(HORIZON);

        assertTrue(horizon.settled());
        for (int frame = 0; frame < LIFT_FRAMES; frame++) {
            assertFalse(horizon.frame(quiet(SHORT_OF_RING_STEP), true));
        }

        horizon.frame(quiet(PAST_RING_STEP), true);
        assertFalse(horizon.locked());
    }

    @Test
    void pressureWithoutALiftLocksNothing() {
        horizon.lower(HORIZON);
        horizon.frame(FakeCameras.underPressure(quiet(EYE)), true);

        assertFalse(horizon.locked());
    }

    @Test
    void aLiftPastTheFarRenderDistanceUnboundsTheHorizon() {
        horizon.lower(NEAR_FAR_DISTANCE);
        lift();

        assertEquals(TreeHorizon.UNBOUNDED, horizon.horizon());
        assertTrue(horizon.settled());
    }

    private void lift() {
        for (int frame = 0; frame < LIFT_FRAMES; frame++) {
            horizon.frame(quiet(EYE), true);
        }
    }

    private static CameraFrame quiet(double x) {
        return FakeCameras.everything(x, EYE, EYE, FAR_CELLS, PIXELS_PER_BLOCK);
    }
}
