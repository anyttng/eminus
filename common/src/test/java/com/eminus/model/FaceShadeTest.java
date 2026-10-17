package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.eminus.VanillaBootstrap;
import com.eminus.model.port.ModelQuad;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FaceShadeTest {
    private static final float VINE_PLANE = 0.8F / 16.0F;
    private static final float NARROW_BAND = 0.25F;
    private static final float WIDE_BAND = 0.75F;
    private static final float[] FACE_UV = {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};
    private static final QuadTexels OPAQUE_WHITE = (quad, u, v) -> 0xFFFF_FFFF;

    private final FaceRasterizer rasterizer = new FaceRasterizer();

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aVineShadedUpTakesTheUpShadeOnBothItsFaces() {
        BakedModel model = rasterize(vine(Direction.UP, Direction.UP));

        assertEquals(ownBut(Direction.UP, Direction.NORTH, Direction.SOUTH), model.shades());
    }

    @Test
    void aQuadWithoutAnOverrideKeepsItsOwnFacesShade() {
        BakedModel model = rasterize(vine(Direction.NORTH, Direction.SOUTH));

        assertEquals(BakedModel.OWN_SHADES, model.shades());
        assertNotEquals(model, rasterize(vine(Direction.UP, Direction.UP)));
    }

    @Test
    void theQuadCoveringMostOfAFaceDecidesItsShade() {
        assertEquals(ownBut(Direction.UP, Direction.SOUTH), rasterize(banded(NARROW_BAND)).shades());
        assertEquals(ownBut(Direction.DOWN, Direction.SOUTH), rasterize(banded(WIDE_BAND)).shades());
    }

    @Test
    void aCrossShadedUpTakesTheUpShadeOnBothBlades() {
        BakedModel model = rasterize(cross());

        assertTrue(ModelMetadata.has(model.metadata(), ModelMetadata.BLADED));
        assertEquals(ownBut(Direction.UP, Direction.DOWN, Direction.UP), model.shades());
    }

    private static int ownBut(Direction shade, Direction... faces) {
        Direction[] shades = Direction.values();
        for (Direction face : faces) {
            shades[face.get3DDataValue()] = shade;
        }
        return BakedModel.packShades(shades);
    }

    private BakedModel rasterize(List<ModelQuad> quads) {
        return rasterizer.rasterize(quads, OPAQUE_WHITE, FaceShadeTest::noTints);
    }

    private static List<ModelQuad> vine(Direction northShade, Direction southShade) {
        return List.of(
                quad(Direction.NORTH, northShade,
                        new float[] {1, 1, VINE_PLANE, 1, 0, VINE_PLANE, 0, 0, VINE_PLANE, 0, 1, VINE_PLANE}),
                quad(Direction.SOUTH, southShade,
                        new float[] {0, 1, VINE_PLANE, 0, 0, VINE_PLANE, 1, 0, VINE_PLANE, 1, 1, VINE_PLANE}));
    }

    private static List<ModelQuad> banded(float top) {
        return List.of(
                quad(Direction.SOUTH, Direction.UP, new float[] {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1}),
                quad(Direction.SOUTH, Direction.DOWN, new float[] {0, 0, 1, 1, 0, 1, 1, top, 1, 0, top, 1}));
    }

    private static List<ModelQuad> cross() {
        return List.of(
                quad(Direction.NORTH, Direction.UP, new float[] {1, 0, 1, 0, 0, 0, 0, 1, 0, 1, 1, 1}),
                quad(Direction.SOUTH, Direction.UP, new float[] {0, 0, 0, 1, 0, 1, 1, 1, 1, 0, 1, 0}),
                quad(Direction.SOUTH, Direction.UP, new float[] {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1}),
                quad(Direction.NORTH, Direction.UP, new float[] {1, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 0}));
    }

    private static ModelQuad quad(Direction face, Direction shade, float[] positions) {
        return FaceRasterizerTest.quad(face, shade, positions, FACE_UV, ModelQuad.NO_TINT);
    }

    private static @Nullable Tint noTints(int layer) {
        return null;
    }
}
