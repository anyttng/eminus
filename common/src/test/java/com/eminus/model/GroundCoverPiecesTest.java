package com.eminus.model;

import static com.eminus.model.FlowerBedQuads.FIRST_STEM_X;
import static com.eminus.model.FlowerBedQuads.FIRST_STEM_Z;
import static com.eminus.model.FlowerBedQuads.LOW_PETALS;
import static com.eminus.model.FlowerBedQuads.MIDDLE_PETALS;
import static com.eminus.model.FlowerBedQuads.SIXTEENTH;
import static com.eminus.model.FlowerBedQuads.TOP_PETALS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.eminus.VanillaBootstrap;
import com.eminus.model.port.ModelQuad;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GroundCoverPiecesTest {
    private static final float EPSILON = 1.0E-4F;
    private static final float LITTER = 0.25F * SIXTEENTH;
    private static final float HALF = 0.5F;
    private static final int PIECES = 5;
    private static final int QUADS_PER_PIECE = 2;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aFlowerBedSplitsIntoItsPetalPlanesFromTheTopAndOneCrossPerStem() {
        List<List<ModelQuad>> pieces = GroundCoverPieces.split(FlowerBedQuads.bed());

        assertEquals(PIECES, pieces.size());
        assertEquals(TOP_PETALS, pieces.get(0).getFirst().corner(0).y(), EPSILON);
        assertEquals(MIDDLE_PETALS, pieces.get(1).getFirst().corner(0).y(), EPSILON);
        assertEquals(LOW_PETALS, pieces.get(2).getFirst().corner(0).y(), EPSILON);
        for (List<ModelQuad> piece : pieces) {
            assertEquals(QUADS_PER_PIECE, piece.size());
        }
    }

    @Test
    void aStemCrossBakesAsBladesAroundItsOwnAxis() {
        List<ModelQuad> stem = FlowerBedQuads.stem(FIRST_STEM_X, FIRST_STEM_Z);

        BakedModel baked = new FaceRasterizer().rasterize(stem, (quad, u, v) -> 0xFFFF_FFFF,
                GroundCoverPiecesTest::noTints);

        assertTrue(ModelMetadata.has(baked.metadata(), ModelMetadata.BLADED));
        assertEquals(FIRST_STEM_X, (baked.bounds()[BakedModel.MIN_X] + baked.bounds()[BakedModel.MAX_X]) * HALF,
                EPSILON);
        assertEquals(FIRST_STEM_Z, (baked.bounds()[BakedModel.MIN_Z] + baked.bounds()[BakedModel.MAX_Z]) * HALF,
                EPSILON);
    }

    @Test
    void oneFlatLayerStaysWhole() {
        List<ModelQuad> litter = FlowerBedQuads.petals(LITTER);

        assertEquals(List.of(litter), GroundCoverPieces.split(litter));
    }

    @Test
    void aQuadNeitherFlatNorOnADiagonalKeepsTheModelWhole() {
        List<ModelQuad> quads = new ArrayList<>(FlowerBedQuads.petals(TOP_PETALS));
        quads.addAll(FlowerBedQuads.petals(LOW_PETALS));
        quads.add(FlowerBedQuads.quad(new float[] {0, 1, 0, 0, 0, 1, 1, 0, 1, 1, 1, 0}));

        assertEquals(List.of(quads), GroundCoverPieces.split(quads));
    }

    private static @Nullable Tint noTints(int layer) {
        return null;
    }
}
