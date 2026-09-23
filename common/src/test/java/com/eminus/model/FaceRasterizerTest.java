package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.FaceMask;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FaceRasterizerTest {
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int GREEN = 0xFF00_FF00;
    private static final int YELLOW = 0xFFFF_FF00;
    private static final int TRANSPARENT = 0x0000_0000;
    private static final float BAND_TOP = 0.5F;
    private static final float CENTRE = 0.5F;
    private static final float HEAD_PLANE = 9.6F / 16.0F;
    private static final float HEAD_BOTTOM = -1.0F / 16.0F;
    private static final float HEAD_TOP = 15.0F / 16.0F;
    private static final float HEAD_NEAR = 1.0F / 16.0F;
    private static final float HEAD_FAR = 15.0F / 16.0F;
    private static final double HEAD_TILT = Math.toRadians(22.5);
    private static final float FLOOR = 0.25F;
    private static final float SLOPE_TOLERANCE = 1.0E-4F;
    private static final int BAND_ROWS = 8;
    private static final int NO_TINT_LAYER = -1;
    private static final int TINT_LAYER = 0;
    private static final int TINT_COLOUR = 0x0033_6699;
    private static final int TINTED_WHITE = 0xFF33_6699;
    private static final int ROW = 3;
    private static final IntFunction<Tint> NO_TINTS = layer -> null;

    private static final float[] NO_UV = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};
    private static final float[] FACE_UV = {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};

    private static final QuadTexels OPAQUE_WHITE = (quad, u, v) -> WHITE;
    private static final QuadTexels FULLY_TRANSPARENT = (quad, u, v) -> TRANSPARENT;
    private static final QuadTexels GREEN_WHERE_TINTED =
            (quad, u, v) -> quad.materialInfo().isTinted() ? GREEN : WHITE;
    private static final QuadTexels HEAD_FRONT_YELLOW_BACK_GREEN = (quad, u, v) -> switch (quad.direction()) {
        case EAST -> YELLOW;
        case WEST -> GREEN;
        default -> WHITE;
    };
    private static final QuadTexels COORDINATES = (quad, u, v) ->
            0xFF00_0000 | (int) (u * BakedModel.FACE_SIDE) << 8 | (int) (v * BakedModel.FACE_SIDE);

    private static BakedQuad.MaterialInfo material;
    private static BakedQuad.MaterialInfo tintedMaterial;

    private final FaceRasterizer rasterizer = new FaceRasterizer();

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        material = new BakedQuad.MaterialInfo(null, ChunkSectionLayer.SOLID, null, NO_TINT_LAYER, true, 0);
        tintedMaterial = new BakedQuad.MaterialInfo(null, ChunkSectionLayer.SOLID, null, TINT_LAYER, true, 0);
    }

    @Test
    void aFullCubeFillsEverySideAndOccludesOnEveryFace() {
        BakedModel model = rasterizer.rasterize(cube(), OPAQUE_WHITE, NO_TINTS);

        assertEquals(FaceMask.ALL, ModelMetadata.present(model.metadata()));
        assertEquals(FaceMask.ALL, ModelMetadata.occluding(model.metadata()));
        assertEquals(FaceMask.ALL, ModelMetadata.occludable(model.metadata()));
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F}, model.insets());
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F}, model.bounds());

        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
                assertEquals(WHITE, model.argb(face, texel), "face " + face + " texel " + texel);
            }
        }
    }

    @Test
    void everyTexelSamplesItsOwnPlaceInTheQuad() {
        BakedModel model = rasterizer.rasterize(cube(), COORDINATES, NO_TINTS);

        int face = Direction.SOUTH.ordinal();
        for (int row = 0; row < BakedModel.FACE_SIDE; row++) {
            for (int column = 0; column < BakedModel.FACE_SIDE; column++) {
                int texel = row * BakedModel.FACE_SIDE + column;
                assertEquals(0xFF00_0000 | column << 8 | row, model.argb(face, texel),
                        "column " + column + " row " + row);
            }
        }
    }

    @Test
    void aBottomSlabOccludesDownwardsOnlyAndSetsItsTopInset() {
        BakedModel model = rasterizer.rasterize(bottomSlab(), OPAQUE_WHITE, NO_TINTS);

        assertEquals(FaceMask.ALL, ModelMetadata.present(model.metadata()));
        assertEquals(FaceMask.DOWN, ModelMetadata.occluding(model.metadata()));
        assertEquals(FaceMask.ALL & ~FaceMask.UP, ModelMetadata.occludable(model.metadata()));
        assertEquals(0.0F, model.insets()[Direction.DOWN.ordinal()]);
        assertEquals(BAND_TOP, model.insets()[Direction.UP.ordinal()]);
        assertEquals(0.0F, model.insets()[Direction.NORTH.ordinal()]);
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 1.0F, BAND_TOP, 1.0F}, model.bounds());

        int face = Direction.SOUTH.ordinal();
        for (int row = 0; row < BakedModel.FACE_SIDE; row++) {
            int expected = row < BAND_ROWS ? WHITE : TRANSPARENT;
            for (int column = 0; column < BakedModel.FACE_SIDE; column++) {
                assertEquals(expected, model.argb(face, row * BakedModel.FACE_SIDE + column), "row " + row);
            }
        }
    }

    @Test
    void aSlopedQuadLandsOnBothFacesItFaces() {
        BakedModel model = rasterizer.rasterize(List.of(ramp()), OPAQUE_WHITE, NO_TINTS);

        assertFalse(ModelMetadata.has(model.metadata(), ModelMetadata.BLADED));
        assertEquals(FaceMask.UP | FaceMask.EAST, ModelMetadata.present(model.metadata()));
    }

    @Test
    void aCrossBakesTwoBladesAndNoBoxFaceAtAll() {
        BakedModel model = rasterizer.rasterize(cross(), OPAQUE_WHITE, NO_TINTS);

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.BLADED));
        assertEquals(FaceMask.NONE, ModelMetadata.present(model.metadata()));

        for (int blade = 0; blade < FaceRasterizer.BLADE_COUNT; blade++) {
            for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
                assertEquals(WHITE, model.argb(blade, texel), "blade " + blade + " texel " + texel);
            }
        }

        for (int face = FaceRasterizer.BLADE_COUNT; face < BakedModel.FACE_COUNT; face++) {
            for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
                assertEquals(TRANSPARENT, model.argb(face, texel), "face " + face + " texel " + texel);
            }
        }
    }

    @Test
    void aCrossWithATiltedHeadKeepsItsBladesAndPaintsTheHeadOntoTheSidesItFaces() {
        BakedModel model = rasterizer.rasterize(sunflowerTop(), HEAD_FRONT_YELLOW_BACK_GREEN, NO_TINTS);

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.BLADED));
        assertEquals(FaceMask.EAST | FaceMask.WEST, ModelMetadata.present(model.metadata()));
        assertEquals(FaceMask.NONE, ModelMetadata.occludable(model.metadata()));

        for (int blade = 0; blade < FaceRasterizer.BLADE_COUNT; blade++) {
            assertTrue(paints(model, blade, WHITE), "blade " + blade);
            assertFalse(paints(model, blade, YELLOW), "blade " + blade);
            assertFalse(paints(model, blade, GREEN), "blade " + blade);
        }

        int east = Direction.EAST.ordinal();
        int west = Direction.WEST.ordinal();
        assertTrue(paints(model, east, YELLOW));
        assertFalse(paints(model, east, WHITE));
        assertTrue(paints(model, west, GREEN));
        assertFalse(paints(model, west, WHITE));
    }

    @Test
    void aTiltedHeadKeepsItsTiltOnTheSidesItFaces() {
        BakedModel model = rasterizer.rasterize(sunflowerTop(), HEAD_FRONT_YELLOW_BACK_GREEN, NO_TINTS);
        float tilt = (float) Math.tan(HEAD_TILT);
        float[] centre = tilted(new float[] {HEAD_PLANE, CENTRE, CENTRE});
        int east = Direction.EAST.ordinal();
        int west = Direction.WEST.ordinal();

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.SLOPED));
        assertEquals(0.0F, model.slopeAlongWidth(east), SLOPE_TOLERANCE);
        assertEquals(tilt, model.slopeAlongHeight(east), SLOPE_TOLERANCE);
        assertEquals(0.0F, model.slopeAlongWidth(west), SLOPE_TOLERANCE);
        assertEquals(-tilt, model.slopeAlongHeight(west), SLOPE_TOLERANCE);
        assertEquals(1.0F - centre[0], model.insets()[east] + tilt * centre[1], SLOPE_TOLERANCE);
        assertEquals(centre[0], model.insets()[west] - tilt * centre[1], SLOPE_TOLERANCE);
    }

    @Test
    void anUprightPlaneBesideACrossIsNotSloped() {
        List<BakedQuad> quads = new ArrayList<>(cross());
        quads.add(quad(Direction.NORTH, new float[] {1, 1, CENTRE, 1, 0, CENTRE, 0, 0, CENTRE, 0, 1, CENTRE}, FACE_UV));
        quads.add(quad(Direction.SOUTH, new float[] {0, 1, CENTRE, 0, 0, CENTRE, 1, 0, CENTRE, 1, 1, CENTRE}, FACE_UV));

        BakedModel model = rasterizer.rasterize(quads, OPAQUE_WHITE, NO_TINTS);

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.BLADED));
        assertEquals(FaceMask.NORTH | FaceMask.SOUTH, ModelMetadata.present(model.metadata()));
        assertFalse(ModelMetadata.has(model.metadata(), ModelMetadata.SLOPED));
        assertEquals(CENTRE, model.insets()[Direction.NORTH.ordinal()], SLOPE_TOLERANCE);
    }

    @Test
    void aCrossWithAFlatPlaneStaysABox() {
        List<BakedQuad> quads = new ArrayList<>(cross());
        quads.add(quad(Direction.UP, new float[] {0, FLOOR, 1, 1, FLOOR, 1, 1, FLOOR, 0, 0, FLOOR, 0}, NO_UV));

        assertFalse(ModelMetadata.has(rasterizer.rasterize(quads, OPAQUE_WHITE, NO_TINTS).metadata(),
                ModelMetadata.BLADED));
    }

    @Test
    void aFullCubeIsNotBladed() {
        assertFalse(ModelMetadata.has(rasterizer.rasterize(cube(), OPAQUE_WHITE, NO_TINTS).metadata(),
                ModelMetadata.BLADED));
    }

    @Test
    void aTintedOverlayMarksItsOwnTexelsAndLeavesEveryOtherFaceUntinted() {
        BakedModel model = rasterizer.rasterize(cubeWithTintedOverlay(), GREEN_WHERE_TINTED, NO_TINTS);

        int overlaid = Direction.SOUTH.ordinal();
        for (int row = 0; row < BakedModel.FACE_SIDE; row++) {
            boolean tinted = row >= BAND_ROWS;
            for (int column = 0; column < BakedModel.FACE_SIDE; column++) {
                assertEquals(tinted, model.tinted(overlaid, row * BakedModel.FACE_SIDE + column), "row " + row);
            }
        }

        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            if (face == overlaid) {
                continue;
            }

            for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
                assertFalse(model.tinted(face, texel), "face " + face + " texel " + texel);
            }
        }
    }

    @Test
    void aConstantTintIsMultipliedIntoTheMaskedTexelsAndClearsTheMask() {
        BakedModel model = rasterizer.rasterize(cubeWithTintedOverlay(), OPAQUE_WHITE,
                layer -> Tint.constant(TINT_COLOUR));

        int overlaid = Direction.SOUTH.ordinal();
        for (int row = 0; row < BakedModel.FACE_SIDE; row++) {
            int expected = row < BAND_ROWS ? WHITE : TINTED_WHITE;
            for (int column = 0; column < BakedModel.FACE_SIDE; column++) {
                int texel = row * BakedModel.FACE_SIDE + column;
                assertEquals(expected, model.argb(overlaid, texel), "row " + row);
                assertFalse(model.tinted(overlaid, texel), "row " + row);
            }
        }

        assertEquals(BiomeColours.NO_ROW, model.tintRow());
        assertFalse(ModelMetadata.has(model.metadata(), ModelMetadata.TINTED));
    }

    @Test
    void aRowTintKeepsTheMaskAndCarriesTheRow() {
        BakedModel model = rasterizer.rasterize(cubeWithTintedOverlay(), OPAQUE_WHITE, layer -> Tint.row(ROW));

        int overlaid = Direction.SOUTH.ordinal();
        assertTrue(model.tinted(overlaid, BakedModel.FACE_TEXELS - 1));
        assertEquals(WHITE, model.argb(overlaid, BakedModel.FACE_TEXELS - 1));
        assertEquals(ROW, model.tintRow());
        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.TINTED));
    }

    @Test
    void aCoplanarQuadPaintsOverTheOneBeforeIt() {
        BakedModel model = rasterizer.rasterize(cubeWithTintedOverlay(), GREEN_WHERE_TINTED, NO_TINTS);

        int overlaid = Direction.SOUTH.ordinal();
        for (int row = 0; row < BakedModel.FACE_SIDE; row++) {
            int expected = row < BAND_ROWS ? WHITE : GREEN;
            for (int column = 0; column < BakedModel.FACE_SIDE; column++) {
                assertEquals(expected, model.argb(overlaid, row * BakedModel.FACE_SIDE + column), "row " + row);
            }
        }
    }

    @Test
    void aFullyTransparentTextureLeavesNoFaceBehind() {
        BakedModel model = rasterizer.rasterize(cube(), FULLY_TRANSPARENT, NO_TINTS);

        assertEquals(FaceMask.NONE, ModelMetadata.present(model.metadata()));
        assertEquals(FaceMask.NONE, ModelMetadata.occludable(model.metadata()));

        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            assertEquals(BakedModel.EMPTY_INSET, model.insets()[face], "face " + face);
        }
    }

    private static List<BakedQuad> cross() {
        return cross(1.0F);
    }

    private static List<BakedQuad> cross(float top) {
        return List.of(
                quad(Direction.NORTH, new float[] {1, 0, 1, 0, 0, 0, 0, top, 0, 1, top, 1}, FACE_UV),
                quad(Direction.SOUTH, new float[] {0, 0, 0, 1, 0, 1, 1, top, 1, 0, top, 0}, FACE_UV),
                quad(Direction.SOUTH, new float[] {0, 0, 1, 1, 0, 0, 1, top, 0, 0, top, 1}, FACE_UV),
                quad(Direction.NORTH, new float[] {1, 0, 0, 0, 0, 1, 0, top, 1, 1, top, 0}, FACE_UV));
    }

    private static List<BakedQuad> sunflowerTop() {
        float x = HEAD_PLANE;
        List<BakedQuad> quads = new ArrayList<>(cross(BAND_TOP));
        quads.add(quad(Direction.EAST, tilted(new float[] {
            x, HEAD_TOP, HEAD_NEAR, x, HEAD_TOP, HEAD_FAR, x, HEAD_BOTTOM, HEAD_FAR, x, HEAD_BOTTOM, HEAD_NEAR}),
                FACE_UV));
        quads.add(quad(Direction.WEST, tilted(new float[] {
            x, HEAD_BOTTOM, HEAD_NEAR, x, HEAD_BOTTOM, HEAD_FAR, x, HEAD_TOP, HEAD_FAR, x, HEAD_TOP, HEAD_NEAR}),
                FACE_UV));
        return quads;
    }

    private static float[] tilted(float[] positions) {
        float cos = (float) Math.cos(HEAD_TILT);
        float sin = (float) Math.sin(HEAD_TILT);
        float[] rotated = positions.clone();
        for (int corner = 0; corner < positions.length; corner += 3) {
            float alongX = positions[corner] - CENTRE;
            float alongY = positions[corner + 1] - CENTRE;
            rotated[corner] = CENTRE + alongX * cos - alongY * sin;
            rotated[corner + 1] = CENTRE + alongX * sin + alongY * cos;
        }

        return rotated;
    }

    private static boolean paints(BakedModel model, int face, int argb) {
        for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
            if (model.argb(face, texel) == argb) {
                return true;
            }
        }

        return false;
    }

    private static BakedQuad ramp() {
        return quad(Direction.UP, new float[] {0, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 0}, FACE_UV);
    }

    private static List<BakedQuad> cube() {
        return List.of(
                quad(Direction.DOWN, new float[] {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, NO_UV),
                quad(Direction.UP, new float[] {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0}, NO_UV),
                quad(Direction.NORTH, new float[] {0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0}, NO_UV),
                quad(Direction.SOUTH, new float[] {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1}, FACE_UV),
                quad(Direction.WEST, new float[] {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0}, NO_UV),
                quad(Direction.EAST, new float[] {1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0}, NO_UV));
    }

    private static List<BakedQuad> bottomSlab() {
        float top = BAND_TOP;
        return List.of(
                quad(Direction.DOWN, new float[] {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, NO_UV),
                quad(Direction.UP, new float[] {0, top, 1, 1, top, 1, 1, top, 0, 0, top, 0}, NO_UV),
                quad(Direction.NORTH, new float[] {0, top, 0, 1, top, 0, 1, 0, 0, 0, 0, 0}, NO_UV),
                quad(Direction.SOUTH, new float[] {0, 0, 1, 1, 0, 1, 1, top, 1, 0, top, 1}, NO_UV),
                quad(Direction.WEST, new float[] {0, 0, 0, 0, 0, 1, 0, top, 1, 0, top, 0}, NO_UV),
                quad(Direction.EAST, new float[] {1, top, 0, 1, top, 1, 1, 0, 1, 1, 0, 0}, NO_UV));
    }

    private static List<BakedQuad> cubeWithTintedOverlay() {
        float bottom = BAND_TOP;
        List<BakedQuad> quads = new ArrayList<>(cube());
        quads.add(quad(Direction.SOUTH,
                new float[] {0, bottom, 1, 1, bottom, 1, 1, 1, 1, 0, 1, 1}, FACE_UV, tintedMaterial));
        return quads;
    }

    private static BakedQuad quad(Direction direction, float[] positions, float[] uvs) {
        return quad(direction, positions, uvs, material);
    }

    private static BakedQuad quad(Direction direction, float[] positions, float[] uvs,
            BakedQuad.MaterialInfo materialInfo) {
        return new BakedQuad(
                new Vector3f(positions[0], positions[1], positions[2]),
                new Vector3f(positions[3], positions[4], positions[5]),
                new Vector3f(positions[6], positions[7], positions[8]),
                new Vector3f(positions[9], positions[10], positions[11]),
                UVPair.pack(uvs[0], uvs[1]),
                UVPair.pack(uvs[2], uvs[3]),
                UVPair.pack(uvs[4], uvs[5]),
                UVPair.pack(uvs[6], uvs[7]),
                direction,
                materialInfo);
    }
}
