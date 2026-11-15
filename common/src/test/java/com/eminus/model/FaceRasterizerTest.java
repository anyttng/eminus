package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.FaceMask;
import com.eminus.model.port.MipStrategy;
import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.Sprite;

import net.minecraft.core.Direction;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FaceRasterizerTest {
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int GREEN = 0xFF00_FF00;
    private static final int YELLOW = 0xFFFF_FF00;
    private static final int TRANSPARENT = 0x0000_0000;
    private static final int OPAQUE = 0xFF00_0000;
    private static final float BAND_TOP = 0.5F;
    private static final float SNOW_TOP = 2.0F / 16.0F;
    private static final float HOLE_FROM = 0.25F;
    private static final float HOLE_TO = 0.75F;
    private static final float CENTER = 0.5F;
    private static final float HEAD_PLANE = 9.6F / 16.0F;
    private static final float HEAD_BOTTOM = -1.0F / 16.0F;
    private static final float HEAD_TOP = 15.0F / 16.0F;
    private static final float HEAD_NEAR = 1.0F / 16.0F;
    private static final float HEAD_FAR = 15.0F / 16.0F;
    private static final double HEAD_TILT = Math.toRadians(22.5);
    private static final float CROSS_FROM = 0.8F / 16.0F;
    private static final float CROSS_TO = 15.2F / 16.0F;
    private static final float STEM_FROM = 5.0F / 16.0F;
    private static final float STEM_TO = 11.0F / 16.0F;
    private static final float STEM_SHIFT = 4.0F / 16.0F;
    private static final float PITCHER_BOTTOM = -5.0F / 16.0F;
    private static final float PITCHER_TOP = 11.0F / 16.0F;
    private static final float FLOOR = 0.25F;
    private static final float RAIL_LOW = 1.0F / 16.0F;
    private static final float RAIL_HIGH = 17.0F / 16.0F;
    private static final float SLOPE_TOLERANCE = 1.0E-4F;
    private static final int BAND_ROWS = 8;
    private static final int TINT_LAYER = 0;
    private static final int ROW = 3;

    private static final float[] NO_UV = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};
    private static final float[] FACE_UV = {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};

    private static final QuadTexels OPAQUE_WHITE = (quad, u, v) -> WHITE;
    private static final QuadTexels FULLY_TRANSPARENT = (quad, u, v) -> TRANSPARENT;
    private static final QuadTexels GREEN_WHERE_TINTED =
            (quad, u, v) -> quad.tinted() ? GREEN : WHITE;
    private static final QuadTexels HEAD_FRONT_YELLOW_BACK_GREEN = (quad, u, v) -> switch (quad.face()) {
        case EAST -> YELLOW;
        case WEST -> GREEN;
        default -> WHITE;
    };
    private static final QuadTexels COORDINATES = (quad, u, v) ->
            0xFF00_0000 | (int) (u * BakedModel.FACE_SIDE) << 8 | (int) (v * BakedModel.FACE_SIDE);


    private final FaceRasterizer rasterizer = new FaceRasterizer();

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aFullCubeFillsEverySideAndOccludesOnEveryFace() {
        BakedModel model = rasterizer.rasterize(cube(), OPAQUE_WHITE, FaceRasterizerTest::noTints);

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
        BakedModel model = rasterizer.rasterize(cube(), COORDINATES, FaceRasterizerTest::noTints);

        int face = Direction.SOUTH.get3DDataValue();
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
        BakedModel model = rasterizer.rasterize(bottomSlab(), OPAQUE_WHITE, FaceRasterizerTest::noTints);

        assertEquals(FaceMask.ALL, ModelMetadata.present(model.metadata()));
        assertEquals(FaceMask.DOWN, ModelMetadata.occluding(model.metadata()));
        assertEquals(FaceMask.ALL & ~FaceMask.UP, ModelMetadata.occludable(model.metadata()));
        assertEquals(0.0F, model.insets()[Direction.DOWN.get3DDataValue()]);
        assertEquals(BAND_TOP, model.insets()[Direction.UP.get3DDataValue()]);
        assertEquals(0.0F, model.insets()[Direction.NORTH.get3DDataValue()]);
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 1.0F, BAND_TOP, 1.0F}, model.bounds());

        int face = Direction.SOUTH.get3DDataValue();
        for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
            assertEquals(WHITE, model.argb(face, texel), "texel " + texel);
        }
    }

    @Test
    void aThinModelsSidesArePaddedPastItsBoundsAndStaySolidOnEveryLevel() {
        for (float top : new float[] {SNOW_TOP, BAND_TOP}) {
            BakedModel model = rasterizer.rasterize(bottomBox(top), OPAQUE_WHITE, FaceRasterizerTest::noTints);

            for (Direction side : Direction.Plane.HORIZONTAL) {
                int face = side.get3DDataValue();
                assertTrue((model.solidFaces() & 1 << face) != 0, side + " under a top of " + top);
                int[] texels = new int[BakedModel.FACE_TEXELS];
                System.arraycopy(model.faces(), face * BakedModel.FACE_TEXELS, texels, 0, BakedModel.FACE_TEXELS);
                int[][] levels = FaceMips.colorLevels(texels, BakedModel.FACE_SIDE, model.mips()[face], false);
                for (int level = 0; level < levels.length; level++) {
                    for (int argb : levels[level]) {
                        assertEquals(OPAQUE, argb & OPAQUE, side + " level " + level + " under a top of " + top);
                    }
                }
            }
        }
    }

    @Test
    void aHoleInsideTheBoundsStaysAHoleAndKeepsTheFaceOutOfTheSolidOnes() {
        List<ModelQuad> quads = new ArrayList<>(bottomBox(BAND_TOP));
        quads.removeIf(quad -> quad.face() == Direction.SOUTH);
        quads.add(quad(Direction.SOUTH, new float[] {
            0, 0, 1, HOLE_FROM, 0, 1, HOLE_FROM, BAND_TOP, 1, 0, BAND_TOP, 1}, NO_UV));
        quads.add(quad(Direction.SOUTH, new float[] {
            HOLE_TO, 0, 1, 1, 0, 1, 1, BAND_TOP, 1, HOLE_TO, BAND_TOP, 1}, NO_UV));

        BakedModel model = rasterizer.rasterize(quads, OPAQUE_WHITE, FaceRasterizerTest::noTints);

        int face = Direction.SOUTH.get3DDataValue();
        int holeColumn = BakedModel.FACE_SIDE / 2;
        for (int row = 0; row < BakedModel.FACE_SIDE; row++) {
            assertEquals(TRANSPARENT, model.argb(face, row * BakedModel.FACE_SIDE + holeColumn), "row " + row);
            assertEquals(WHITE, model.argb(face, row * BakedModel.FACE_SIDE), "row " + row);
        }
        assertEquals(0, model.solidFaces() & 1 << face);
    }

    @Test
    void aTintedOverlayIsPaddedWithItsTintAndTheLayerUnderIt() {
        Sprite base = new LayerSprite(MipStrategy.MEAN);
        Sprite overlay = new LayerSprite(MipStrategy.CUTOUT);
        float[] south = {0, 0, 1, 1, 0, 1, 1, BAND_TOP, 1, 0, BAND_TOP, 1};
        List<ModelQuad> quads = List.of(layer(south, base, ModelQuad.NO_TINT), layer(south, overlay, TINT_LAYER));
        QuadTexels texels = (quad, u, v) -> base.equals(quad.sprite()) ? YELLOW : WHITE;

        BakedModel model = rasterizer.rasterize(quads, texels, FaceRasterizerTest::noTints);

        int face = Direction.SOUTH.get3DDataValue();
        BakedModel layer = Objects.requireNonNull(model.overlay());
        for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
            assertEquals(YELLOW, model.argb(face, texel), "texel " + texel);
            assertFalse(model.tinted(face, texel), "texel " + texel);
            assertEquals(WHITE, layer.argb(face, texel), "texel " + texel);
            assertTrue(layer.tinted(face, texel), "texel " + texel);
        }
    }

    @Test
    void aTintedCutoutLayerOverAnotherBakesIntoABaseFreeOfItAndAnOverlayOfItsOwn() {
        Sprite base = new LayerSprite(MipStrategy.MEAN);
        Sprite overlay = new LayerSprite(MipStrategy.CUTOUT);
        float[] south = {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1};
        List<ModelQuad> quads = List.of(layer(south, base, ModelQuad.NO_TINT), layer(south, overlay, TINT_LAYER));
        QuadTexels texels = (quad, u, v) -> base.equals(quad.sprite()) ? YELLOW : v < BAND_TOP ? WHITE : TRANSPARENT;

        BakedModel model = rasterizer.rasterize(quads, texels, FaceRasterizerTest::noTints);

        int face = Direction.SOUTH.get3DDataValue();
        BakedModel layer = Objects.requireNonNull(model.overlay());
        assertTrue(layer.layer());
        assertFalse(model.layer());
        assertEquals(FaceMask.SOUTH, ModelMetadata.present(layer.metadata()));
        assertEquals(FaceMask.NONE, ModelMetadata.occluding(layer.metadata()));
        assertEquals(MipStrategy.CUTOUT, layer.mips()[face].strategy());
        int whiteTexels = 0;
        for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
            boolean white = layer.argb(face, texel) == WHITE;
            whiteTexels += white ? 1 : 0;
            assertEquals(white ? WHITE : TRANSPARENT, layer.argb(face, texel), "texel " + texel);
            assertEquals(white, layer.tinted(face, texel), "texel " + texel);
            assertEquals(YELLOW, model.argb(face, texel), "texel " + texel);
            assertFalse(model.tinted(face, texel), "texel " + texel);
        }
        assertEquals(BakedModel.FACE_TEXELS / 2, whiteTexels);
        assertTrue((model.solidFaces() & 1 << face) != 0);

        int[] layerFace = Arrays.copyOfRange(layer.faces(), face * BakedModel.FACE_TEXELS,
                (face + 1) * BakedModel.FACE_TEXELS);
        int[][] overlayLevels = FaceMips.colorLevels(layerFace, BakedModel.FACE_SIDE, layer.mips()[face], false);
        int mean = overlayLevels[overlayLevels.length - 1][0];
        assertTrue((mean & OPAQUE) != OPAQUE, "the overlay's last level keeps its own alpha");
    }

    @Test
    void aFaceWithNoLayerOverItBakesNoOverlay() {
        BakedModel model = rasterizer.rasterize(bottomBox(BAND_TOP), OPAQUE_WHITE, FaceRasterizerTest::noTints);

        assertNull(model.overlay());
    }

    @Test
    void aSlopedQuadIsKeptOnOneFaceOnly() {
        BakedModel model = rasterizer.rasterize(List.of(ramp()), OPAQUE_WHITE, FaceRasterizerTest::noTints);

        assertFalse(ModelMetadata.has(model.metadata(), ModelMetadata.BLADED));
        assertEquals(FaceMask.UP, ModelMetadata.present(model.metadata()));
    }

    @Test
    void aRaisedRailKeepsItsSlopeOnItsUpAndDownFacesAlone() {
        BakedModel model = rasterizer.rasterize(raisedRail(), OPAQUE_WHITE, FaceRasterizerTest::noTints);
        int down = Direction.DOWN.get3DDataValue();
        int up = Direction.UP.get3DDataValue();

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.SLOPED));
        assertEquals(FaceMask.DOWN | FaceMask.UP, ModelMetadata.present(model.metadata()));
        assertEquals(FaceMask.NONE, ModelMetadata.occludable(model.metadata()));
        assertEquals(0.0F, model.slopeAlongWidth(up), SLOPE_TOLERANCE);
        assertEquals(1.0F, model.slopeAlongHeight(up), SLOPE_TOLERANCE);
        assertEquals(1.0F - RAIL_HIGH, model.insets()[up], SLOPE_TOLERANCE);
        assertEquals(0.0F, model.slopeAlongWidth(down), SLOPE_TOLERANCE);
        assertEquals(-1.0F, model.slopeAlongHeight(down), SLOPE_TOLERANCE);
        assertEquals(RAIL_HIGH, model.insets()[down], SLOPE_TOLERANCE);
        assertEquals(BakedModel.EMPTY_INSET, model.insets()[Direction.NORTH.get3DDataValue()]);
        assertEquals(BakedModel.EMPTY_INSET, model.insets()[Direction.SOUTH.get3DDataValue()]);
    }

    @Test
    void aFullCubeAndABottomSlabAreNotSloped() {
        for (List<ModelQuad> quads : List.of(cube(), bottomSlab())) {
            BakedModel model = rasterizer.rasterize(quads, OPAQUE_WHITE, FaceRasterizerTest::noTints);

            assertFalse(ModelMetadata.has(model.metadata(), ModelMetadata.SLOPED));
            assertArrayEquals(new float[BakedModel.SLOPES_LENGTH], model.slopes());
        }
    }

    @Test
    void aFaceWhoseQuadsLieInTwoPlanesIsNotSloped() {
        List<ModelQuad> quads = List.of(ramp(),
                quad(Direction.UP, new float[] {0, FLOOR, 1, 1, FLOOR, 1, 1, FLOOR, 0, 0, FLOOR, 0}, NO_UV));
        BakedModel model = rasterizer.rasterize(quads, OPAQUE_WHITE, FaceRasterizerTest::noTints);
        int up = Direction.UP.get3DDataValue();

        assertTrue((ModelMetadata.present(model.metadata()) & FaceMask.UP) != 0);
        assertEquals(0.0F, model.slopeAlongWidth(up));
        assertEquals(0.0F, model.slopeAlongHeight(up));
    }

    @Test
    void theQuadTestTellsACrossBeforeAnyRasterizing() {
        assertTrue(FaceRasterizer.bladed(cross()));
        assertTrue(FaceRasterizer.bladed(sunflowerTop()));
        assertFalse(FaceRasterizer.bladed(cube()));
        assertFalse(FaceRasterizer.bladed(raisedRail()));
    }

    @Test
    void aCrossBakesTwoBladesAndNoBoxFaceAtAll() {
        BakedModel model = rasterizer.rasterize(cross(), OPAQUE_WHITE, FaceRasterizerTest::noTints);

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
    void aCrossBladeHoldsEachSpriteColumnInOneImageColumn() {
        assertSpriteFillsBladeImage(List.of(blade(CROSS_FROM, CROSS_TO, 0.0F, 0.0F, 1.0F)));
    }

    @Test
    void aBladeOffTheVoxelCenterStartsItsSpriteAtItsBoundsEdge() {
        assertSpriteFillsBladeImage(List.of(blade(STEM_FROM, STEM_TO, STEM_SHIFT, 0.0F, 1.0F)));
    }

    @Test
    void aBladeReachingBelowTheBlockPutsItsSpritesBottomRowIntoTheImagesFirst() {
        assertSpriteFillsBladeImage(List.of(blade(0.0F, 1.0F, 0.0F, PITCHER_BOTTOM, PITCHER_TOP)));
    }

    @Test
    void aCrossWithATiltedHeadKeepsItsBladesAndPaintsTheHeadOntoTheSidesItFaces() {
        BakedModel model = rasterizer.rasterize(sunflowerTop(), HEAD_FRONT_YELLOW_BACK_GREEN, FaceRasterizerTest::noTints);

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.BLADED));
        assertEquals(FaceMask.EAST | FaceMask.WEST, ModelMetadata.present(model.metadata()));
        assertEquals(FaceMask.NONE, ModelMetadata.occludable(model.metadata()));

        for (int blade = 0; blade < FaceRasterizer.BLADE_COUNT; blade++) {
            assertTrue(paints(model, blade, WHITE), "blade " + blade);
            assertFalse(paints(model, blade, YELLOW), "blade " + blade);
            assertFalse(paints(model, blade, GREEN), "blade " + blade);
        }

        int east = Direction.EAST.get3DDataValue();
        int west = Direction.WEST.get3DDataValue();
        assertTrue(paints(model, east, YELLOW));
        assertFalse(paints(model, east, WHITE));
        assertTrue(paints(model, west, GREEN));
        assertFalse(paints(model, west, WHITE));
    }

    @Test
    void aTiltedHeadKeepsItsTiltOnTheSidesItFaces() {
        BakedModel model = rasterizer.rasterize(sunflowerTop(), HEAD_FRONT_YELLOW_BACK_GREEN, FaceRasterizerTest::noTints);
        float tilt = (float) Math.tan(HEAD_TILT);
        float[] center = tilted(new float[] {HEAD_PLANE, CENTER, CENTER});
        int east = Direction.EAST.get3DDataValue();
        int west = Direction.WEST.get3DDataValue();

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.SLOPED));
        assertEquals(0.0F, model.slopeAlongWidth(east), SLOPE_TOLERANCE);
        assertEquals(tilt, model.slopeAlongHeight(east), SLOPE_TOLERANCE);
        assertEquals(0.0F, model.slopeAlongWidth(west), SLOPE_TOLERANCE);
        assertEquals(-tilt, model.slopeAlongHeight(west), SLOPE_TOLERANCE);
        assertEquals(1.0F - center[0], model.insets()[east] + tilt * center[1], SLOPE_TOLERANCE);
        assertEquals(center[0], model.insets()[west] - tilt * center[1], SLOPE_TOLERANCE);
    }

    @Test
    void anUprightPlaneBesideACrossIsNotSloped() {
        List<ModelQuad> quads = new ArrayList<>(cross());
        quads.add(quad(Direction.NORTH, new float[] {1, 1, CENTER, 1, 0, CENTER, 0, 0, CENTER, 0, 1, CENTER}, FACE_UV));
        quads.add(quad(Direction.SOUTH, new float[] {0, 1, CENTER, 0, 0, CENTER, 1, 0, CENTER, 1, 1, CENTER}, FACE_UV));

        BakedModel model = rasterizer.rasterize(quads, OPAQUE_WHITE, FaceRasterizerTest::noTints);

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.BLADED));
        assertEquals(FaceMask.NORTH | FaceMask.SOUTH, ModelMetadata.present(model.metadata()));
        assertFalse(ModelMetadata.has(model.metadata(), ModelMetadata.SLOPED));
        assertEquals(CENTER, model.insets()[Direction.NORTH.get3DDataValue()], SLOPE_TOLERANCE);
    }

    @Test
    void aCrossWithAFlatPlaneStaysABox() {
        List<ModelQuad> quads = new ArrayList<>(cross());
        quads.add(quad(Direction.UP, new float[] {0, FLOOR, 1, 1, FLOOR, 1, 1, FLOOR, 0, 0, FLOOR, 0}, NO_UV));

        assertFalse(ModelMetadata.has(rasterizer.rasterize(quads, OPAQUE_WHITE, FaceRasterizerTest::noTints).metadata(),
                ModelMetadata.BLADED));
    }

    @Test
    void aFullCubeIsNotBladed() {
        assertFalse(ModelMetadata.has(rasterizer.rasterize(cube(), OPAQUE_WHITE, FaceRasterizerTest::noTints).metadata(),
                ModelMetadata.BLADED));
    }

    @Test
    void aTintedOverlayMarksItsOwnTexelsAndLeavesEveryOtherFaceUntinted() {
        BakedModel model = rasterizer.rasterize(cubeWithTintedOverlay(), GREEN_WHERE_TINTED, FaceRasterizerTest::noTints);

        int overlaid = Direction.SOUTH.get3DDataValue();
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
    void aTintWithoutARowLeavesTheTexelsAsPaintedAndClearsTheMask() {
        BakedModel model = rasterizer.rasterize(cubeWithTintedOverlay(), OPAQUE_WHITE, layer -> Tint.UNTINTED);

        int overlaid = Direction.SOUTH.get3DDataValue();
        for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
            assertEquals(WHITE, model.argb(overlaid, texel), "texel " + texel);
            assertFalse(model.tinted(overlaid, texel), "texel " + texel);
        }

        assertEquals(BiomeColors.NO_ROW, model.tintRow());
        assertFalse(ModelMetadata.has(model.metadata(), ModelMetadata.TINTED));
    }

    @Test
    void aRowTintKeepsTheMaskAndCarriesTheRow() {
        BakedModel model = rasterizer.rasterize(cubeWithTintedOverlay(), OPAQUE_WHITE, layer -> Tint.row(ROW));

        int overlaid = Direction.SOUTH.get3DDataValue();
        assertTrue(model.tinted(overlaid, BakedModel.FACE_TEXELS - 1));
        assertEquals(WHITE, model.argb(overlaid, BakedModel.FACE_TEXELS - 1));
        assertEquals(ROW, model.tintRow());
        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.TINTED));
    }

    @Test
    void aCoplanarQuadPaintsOverTheOneBeforeIt() {
        BakedModel model = rasterizer.rasterize(cubeWithTintedOverlay(), GREEN_WHERE_TINTED, FaceRasterizerTest::noTints);

        int overlaid = Direction.SOUTH.get3DDataValue();
        for (int row = 0; row < BakedModel.FACE_SIDE; row++) {
            int expected = row < BAND_ROWS ? WHITE : GREEN;
            for (int column = 0; column < BakedModel.FACE_SIDE; column++) {
                assertEquals(expected, model.argb(overlaid, row * BakedModel.FACE_SIDE + column), "row " + row);
            }
        }
    }

    @Test
    void aFullyTransparentTextureLeavesNoFaceBehind() {
        BakedModel model = rasterizer.rasterize(cube(), FULLY_TRANSPARENT, FaceRasterizerTest::noTints);

        assertEquals(FaceMask.NONE, ModelMetadata.present(model.metadata()));
        assertEquals(FaceMask.NONE, ModelMetadata.occludable(model.metadata()));

        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            assertEquals(BakedModel.EMPTY_INSET, model.insets()[face], "face " + face);
        }
    }

    @Test
    void aForcedOpaqueTextureKeepsItsHolesAndOccludesOnEveryFace() {
        BakedModel model = rasterizer.rasterize(cube(), FULLY_TRANSPARENT, FaceRasterizerTest::noTints, true);

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.OPAQUE));
        assertEquals(FaceMask.ALL, ModelMetadata.occluding(model.metadata()));
        assertTrue(paints(model, Direction.UP.get3DDataValue(), TRANSPARENT));
        assertFalse(paints(model, Direction.UP.get3DDataValue(), WHITE));
    }

    private static List<ModelQuad> cross() {
        return cross(1.0F);
    }

    private static List<ModelQuad> cross(float top) {
        return List.of(
                quad(Direction.NORTH, new float[] {1, 0, 1, 0, 0, 0, 0, top, 0, 1, top, 1}, FACE_UV),
                quad(Direction.SOUTH, new float[] {0, 0, 0, 1, 0, 1, 1, top, 1, 0, top, 0}, FACE_UV),
                quad(Direction.SOUTH, new float[] {0, 0, 1, 1, 0, 0, 1, top, 0, 0, top, 1}, FACE_UV),
                quad(Direction.NORTH, new float[] {1, 0, 0, 0, 0, 1, 0, top, 1, 1, top, 0}, FACE_UV));
    }

    private static ModelQuad blade(float from, float to, float shift, float bottom, float top) {
        return quad(Direction.NORTH, new float[] {
            to, bottom, to + shift, from, bottom, from + shift, from, top, from + shift, to, top, to + shift},
                FACE_UV);
    }

    private void assertSpriteFillsBladeImage(List<ModelQuad> quads) {
        BakedModel model = rasterizer.rasterize(quads, COORDINATES, FaceRasterizerTest::noTints);

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.BLADED));
        for (int row = 0; row < BakedModel.FACE_SIDE; row++) {
            for (int column = 0; column < BakedModel.FACE_SIDE; column++) {
                int spriteColumn = BakedModel.FACE_SIDE - 1 - column;
                assertEquals(0xFF00_0000 | spriteColumn << 8 | row,
                        model.argb(0, row * BakedModel.FACE_SIDE + column), "column " + column + " row " + row);
            }
        }
    }

    private static List<ModelQuad> sunflowerTop() {
        float x = HEAD_PLANE;
        List<ModelQuad> quads = new ArrayList<>(cross(BAND_TOP));
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
            float alongX = positions[corner] - CENTER;
            float alongY = positions[corner + 1] - CENTER;
            rotated[corner] = CENTER + alongX * cos - alongY * sin;
            rotated[corner + 1] = CENTER + alongX * sin + alongY * cos;
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

    private static List<ModelQuad> raisedRail() {
        return List.of(
                quad(Direction.UP, new float[] {
                    0, RAIL_LOW, 1, 1, RAIL_LOW, 1, 1, RAIL_HIGH, 0, 0, RAIL_HIGH, 0}, FACE_UV),
                quad(Direction.DOWN, new float[] {
                    0, RAIL_HIGH, 0, 1, RAIL_HIGH, 0, 1, RAIL_LOW, 1, 0, RAIL_LOW, 1}, FACE_UV));
    }

    private static ModelQuad ramp() {
        return quad(Direction.UP, new float[] {0, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 0}, FACE_UV);
    }

    private static List<ModelQuad> cube() {
        return List.of(
                quad(Direction.DOWN, new float[] {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, NO_UV),
                quad(Direction.UP, new float[] {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0}, NO_UV),
                quad(Direction.NORTH, new float[] {0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0}, NO_UV),
                quad(Direction.SOUTH, new float[] {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1}, FACE_UV),
                quad(Direction.WEST, new float[] {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0}, NO_UV),
                quad(Direction.EAST, new float[] {1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0}, NO_UV));
    }

    private static List<ModelQuad> bottomSlab() {
        return bottomBox(BAND_TOP);
    }

    private static List<ModelQuad> bottomBox(float top) {
        return List.of(
                quad(Direction.DOWN, new float[] {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, NO_UV),
                quad(Direction.UP, new float[] {0, top, 1, 1, top, 1, 1, top, 0, 0, top, 0}, NO_UV),
                quad(Direction.NORTH, new float[] {0, top, 0, 1, top, 0, 1, 0, 0, 0, 0, 0}, NO_UV),
                quad(Direction.SOUTH, new float[] {0, 0, 1, 1, 0, 1, 1, top, 1, 0, top, 1}, NO_UV),
                quad(Direction.WEST, new float[] {0, 0, 0, 0, 0, 1, 0, top, 1, 0, top, 0}, NO_UV),
                quad(Direction.EAST, new float[] {1, top, 0, 1, top, 1, 1, 0, 1, 1, 0, 0}, NO_UV));
    }

    private static List<ModelQuad> cubeWithTintedOverlay() {
        float bottom = BAND_TOP;
        List<ModelQuad> quads = new ArrayList<>(cube());
        quads.add(quad(Direction.SOUTH,
                new float[] {0, bottom, 1, 1, bottom, 1, 1, 1, 1, 0, 1, 1}, FACE_UV, TINT_LAYER));
        return quads;
    }

    private static ModelQuad quad(Direction direction, float[] positions, float[] uvs) {
        return quad(direction, positions, uvs, ModelQuad.NO_TINT);
    }

    private static ModelQuad quad(Direction direction, float[] positions, float[] uvs, int tintLayer) {
        return quad(direction, direction, positions, uvs, tintLayer);
    }

    static ModelQuad quad(Direction direction, Direction shade, float[] positions, float[] uvs, int tintLayer) {
        return new ModelQuad(new Vector3fc[] {
            new Vector3f(positions[0], positions[1], positions[2]),
            new Vector3f(positions[3], positions[4], positions[5]),
            new Vector3f(positions[6], positions[7], positions[8]),
            new Vector3f(positions[9], positions[10], positions[11])},
                uvs.clone(), null, tintLayer, false, 0, direction, shade, true);
    }

    private static ModelQuad layer(float[] positions, Sprite sprite, int tintLayer) {
        return new ModelQuad(new Vector3fc[] {
            new Vector3f(positions[0], positions[1], positions[2]),
            new Vector3f(positions[3], positions[4], positions[5]),
            new Vector3f(positions[6], positions[7], positions[8]),
            new Vector3f(positions[9], positions[10], positions[11])},
                FACE_UV.clone(), sprite, tintLayer, false, 0, Direction.SOUTH, Direction.SOUTH, true);
    }

    private record LayerSprite(MipStrategy mipStrategy) implements Sprite {
        @Override
        public int width() {
            return BakedModel.FACE_SIDE;
        }

        @Override
        public int height() {
            return BakedModel.FACE_SIDE;
        }

        @Override
        public float u0() {
            return 0.0F;
        }

        @Override
        public float u1() {
            return 1.0F;
        }

        @Override
        public float v0() {
            return 0.0F;
        }

        @Override
        public float v1() {
            return 1.0F;
        }

        @Override
        public int[] argb() {
            return new int[0];
        }

        @Override
        public float alphaCutoffBias() {
            return 0.0F;
        }
    }

    private static @Nullable Tint noTints(int layer) {
        return null;
    }
}
