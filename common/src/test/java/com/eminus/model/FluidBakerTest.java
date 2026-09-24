package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import com.eminus.cell.FaceMask;

import org.junit.jupiter.api.Test;

class FluidBakerTest {
    private static final int OPAQUE_TEXEL = 0xFFCF5A1E;
    private static final float EIGHT_NINTHS = 8.0F / 9.0F;
    private static final float ONE_NINTH = 1.0F / 9.0F;
    private static final float TOLERANCE = 1.0E-6F;
    private static final int METADATA = 0x1234;
    private static final int TINT_ROW = 3;

    @Test
    void theSurfaceSitsAtTheHeightTheStateDrawsAtInTheGame() {
        assertArrayEquals(new float[] {0.0F, 1.0F - EIGHT_NINTHS, 0.0F, 0.0F, 0.0F, 0.0F},
                FluidBaker.surfaceInsets(EIGHT_NINTHS), TOLERANCE);
        assertArrayEquals(new float[] {0.0F, 1.0F - ONE_NINTH, 0.0F, 0.0F, 0.0F, 0.0F},
                FluidBaker.surfaceInsets(ONE_NINTH), TOLERANCE);
    }

    @Test
    void theSubmergedTwinReachesTheTopOfTheBlockAndKeepsItsTexelsAndTint() {
        BakedModel surface = new BakedModel(new int[BakedModel.FACE_COUNT * BakedModel.FACE_TEXELS],
                BakedModel.tintedMask(), FluidBaker.surfaceInsets(ONE_NINTH), FluidBaker.surfaceBounds(ONE_NINTH),
                METADATA, TINT_ROW);

        BakedModel submerged = FluidBaker.submerged(surface);

        assertArrayEquals(new float[BakedModel.FACE_COUNT], submerged.insets(), TOLERANCE);
        assertArrayEquals(BakedModel.fullBounds(), submerged.bounds(), TOLERANCE);
        assertEquals(TINT_ROW, submerged.tintRow());
        assertArrayEquals(surface.tintMask(), submerged.tintMask());
        assertArrayEquals(surface.faces(), submerged.faces());
    }

    @Test
    void anOpaqueSurfaceOccludesItsBottomAlone() {
        assertEquals(FaceMask.DOWN, FluidBaker.occluding(FluidBaker.surfaceBounds(EIGHT_NINTHS), false));
        assertEquals(FaceMask.DOWN, FluidBaker.occluding(FluidBaker.surfaceBounds(ONE_NINTH), false));
        assertEquals(FaceMask.ALL & ~FaceMask.UP, FluidBaker.occludable(FluidBaker.surfaceBounds(EIGHT_NINTHS)));
    }

    @Test
    void aSeeThroughFluidOccludesNothing() {
        assertEquals(FaceMask.NONE, FluidBaker.occluding(FluidBaker.surfaceBounds(EIGHT_NINTHS), true));
        assertEquals(FaceMask.NONE, FluidBaker.occluding(BakedModel.fullBounds(), true));
    }

    @Test
    void theSubmergedTwinOfAnOpaqueFluidOccludesAndCanBeOccludedOnEveryFace() {
        int flags = ModelMetadata.TINTED;
        BakedModel submerged = FluidBaker.submerged(surface(OPAQUE_TEXEL, flags));

        assertEquals(FaceMask.ALL, ModelMetadata.occluding(submerged.metadata()));
        assertEquals(FaceMask.ALL, ModelMetadata.occludable(submerged.metadata()));
        assertEquals(FaceMask.ALL, ModelMetadata.present(submerged.metadata()));
        assertEquals(ModelMetadata.MAX_EMISSION, ModelMetadata.emission(submerged.metadata()));
        assertEquals(flags, submerged.metadata() & ModelMetadata.FLAGS);
    }

    @Test
    void theSubmergedTwinOfATranslucentFluidOccludesNothing() {
        BakedModel submerged = FluidBaker.submerged(surface(OPAQUE_TEXEL, ModelMetadata.TRANSLUCENT));

        assertEquals(FaceMask.NONE, ModelMetadata.occluding(submerged.metadata()));
        assertEquals(FaceMask.ALL, ModelMetadata.occludable(submerged.metadata()));
    }

    private static BakedModel surface(int texel, int flags) {
        int[] faces = new int[BakedModel.FACE_COUNT * BakedModel.FACE_TEXELS];
        Arrays.fill(faces, texel);
        float[] bounds = FluidBaker.surfaceBounds(EIGHT_NINTHS);
        boolean seeThrough = ModelMetadata.has(flags, ModelMetadata.TRANSLUCENT);
        int metadata = ModelMetadata.pack(FaceMask.ALL, FluidBaker.occluding(bounds, seeThrough),
                FluidBaker.occludable(bounds), ModelMetadata.MAX_EMISSION, flags);
        return new BakedModel(faces, BakedModel.untintedMask(), FluidBaker.surfaceInsets(EIGHT_NINTHS), bounds,
                metadata, TINT_ROW);
    }

    @Test
    void theSidesStopAtTheSurface() {
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 1.0F, EIGHT_NINTHS, 1.0F},
                FluidBaker.surfaceBounds(EIGHT_NINTHS), TOLERANCE);
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 1.0F, ONE_NINTH, 1.0F},
                FluidBaker.surfaceBounds(ONE_NINTH), TOLERANCE);
    }
}
