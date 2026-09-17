package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FluidBakerTest {
    private static final float EIGHT_NINTHS = 8.0F / 9.0F;
    private static final float TOLERANCE = 1.0E-6F;
    private static final int METADATA = 0x1234;
    private static final int TINT_ROW = 3;

    @Test
    void theSurfaceSitsAtEightNinthsOfTheBlockLikeTheGamesFluidRenderer() {
        assertArrayEquals(new float[] {0.0F, 1.0F - EIGHT_NINTHS, 0.0F, 0.0F, 0.0F, 0.0F},
                FluidBaker.surfaceInsets(), TOLERANCE);
    }

    @Test
    void theSubmergedTwinReachesTheTopOfTheBlockAndKeepsEverythingElse() {
        BakedModel surface = new BakedModel(new int[BakedModel.FACE_COUNT * BakedModel.FACE_TEXELS],
                BakedModel.tintedMask(), FluidBaker.surfaceInsets(), FluidBaker.surfaceBounds(), METADATA, TINT_ROW);

        BakedModel submerged = FluidBaker.submerged(surface);

        assertArrayEquals(new float[BakedModel.FACE_COUNT], submerged.insets(), TOLERANCE);
        assertArrayEquals(BakedModel.fullBounds(), submerged.bounds(), TOLERANCE);
        assertEquals(METADATA, submerged.metadata());
        assertEquals(TINT_ROW, submerged.tintRow());
        assertArrayEquals(surface.tintMask(), submerged.tintMask());
    }

    @Test
    void theSidesStopAtTheSurface() {
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 1.0F, EIGHT_NINTHS, 1.0F},
                FluidBaker.surfaceBounds(), TOLERANCE);
    }
}
