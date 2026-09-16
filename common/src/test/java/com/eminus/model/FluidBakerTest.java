package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class FluidBakerTest {
    private static final float EIGHT_NINTHS = 8.0F / 9.0F;
    private static final float TOLERANCE = 1.0E-6F;

    @Test
    void theSurfaceSitsAtEightNinthsOfTheBlockLikeTheGamesFluidRenderer() {
        assertArrayEquals(new float[] {0.0F, 1.0F - EIGHT_NINTHS, 0.0F, 0.0F, 0.0F, 0.0F},
                FluidBaker.surfaceInsets(), TOLERANCE);
    }

    @Test
    void theSidesStopAtTheSurface() {
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 1.0F, EIGHT_NINTHS, 1.0F},
                FluidBaker.surfaceBounds(), TOLERANCE);
    }
}
