package com.eminus.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CameraOriginTest {
    @Test
    void aPositiveCoordinateSplitsIntoItsBlockAndANegativeOffset() {
        assertEquals(new CameraOrigin(10, 64, 3, -0.25F, -0.5F, -0.75F), CameraOrigin.of(10.25, 64.5, 3.75));
    }

    @Test
    void aNegativeCoordinateFloorsAwayFromZero() {
        assertEquals(new CameraOrigin(-1, -64, -11, -0.75F, -0.5F, -0.25F), CameraOrigin.of(-0.25, -63.5, -10.75));
    }

    @Test
    void aWholeCoordinateHasNoOffset() {
        assertEquals(new CameraOrigin(-3, 0, 7, 0.0F, 0.0F, 0.0F), CameraOrigin.of(-3.0, 0.0, 7.0));
    }

    @Test
    void aFarCoordinateKeepsItsFractionBecauseTheSubtractionRunsInDoubles() {
        assertEquals(new CameraOrigin(29_999_984, 320, -29_999_985, -0.125F, -0.0625F, -0.125F),
                CameraOrigin.of(29_999_984.125, 320.0625, -29_999_984.875));
    }
}
