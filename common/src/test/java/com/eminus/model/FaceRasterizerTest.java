package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.IntFunction;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.FaceMask;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FaceRasterizerTest {
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int TRANSPARENT = 0x0000_0000;
    private static final float BAND_TOP = 0.5F;
    private static final int BAND_ROWS = 8;
    private static final int NO_TINT_LAYER = -1;
    private static final IntFunction<BlockTintSource> NO_TINTS = layer -> null;

    private static final float[] NO_UV = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};
    private static final float[] FACE_UV = {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};

    private static final QuadTexels OPAQUE_WHITE = (quad, u, v) -> WHITE;
    private static final QuadTexels FULLY_TRANSPARENT = (quad, u, v) -> TRANSPARENT;
    private static final QuadTexels COORDINATES = (quad, u, v) ->
            0xFF00_0000 | (int) (u * BakedModel.FACE_SIDE) << 8 | (int) (v * BakedModel.FACE_SIDE);

    private static BakedQuad.MaterialInfo material;

    private final FaceRasterizer rasterizer = new FaceRasterizer();

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        material = new BakedQuad.MaterialInfo(null, ChunkSectionLayer.SOLID, null, NO_TINT_LAYER, true, 0);
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
    void aFullCubeIsNotBladed() {
        assertFalse(ModelMetadata.has(rasterizer.rasterize(cube(), OPAQUE_WHITE, NO_TINTS).metadata(),
                ModelMetadata.BLADED));
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
        return List.of(
                quad(Direction.NORTH, new float[] {1, 0, 1, 0, 0, 0, 0, 1, 0, 1, 1, 1}, FACE_UV),
                quad(Direction.SOUTH, new float[] {0, 0, 0, 1, 0, 1, 1, 1, 1, 0, 1, 0}, FACE_UV),
                quad(Direction.SOUTH, new float[] {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1}, FACE_UV),
                quad(Direction.NORTH, new float[] {1, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 0}, FACE_UV));
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

    private static BakedQuad quad(Direction direction, float[] positions, float[] uvs) {
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
                material);
    }
}
