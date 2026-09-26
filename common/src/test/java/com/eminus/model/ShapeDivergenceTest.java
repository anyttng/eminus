package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.FaceMask;
import com.eminus.model.port.ModelQuad;

import net.minecraft.core.Direction;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShapeDivergenceTest {
    private static final float EPSILON = 1.0E-4F;
    private static final float SIXTEENTH = 1.0F / 16.0F;
    private static final float HALF = 0.5F;
    private static final float BLADE_FROM = 0.8F * SIXTEENTH;
    private static final float BLADE_TO = 15.2F * SIXTEENTH;
    private static final IntFunction<Tint> NO_TINTS = layer -> null;
    private static final QuadTexels OPAQUE_WHITE = (quad, u, v) -> 0xFFFF_FFFF;
    private static final ShapeDivergence.SpriteColumns SIXTEEN_COLUMNS =
            (quad, uSpan) -> uSpan * BakedModel.FACE_SIDE;

    private static final float[][] CUBE = {
        {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
        {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0},
        {0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0},
        {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1},
        {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
        {1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0}};
    private static final float[] FACE_UV = {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};

    private final FaceRasterizer rasterizer = new FaceRasterizer();

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aSlabBakedFromItsOwnQuadsDivergesNowhere() {
        List<ModelQuad> slab = box(0, 0, 0, 1, HALF, 1);

        ShapeDivergence divergence = measure(slab, bake(slab));

        assertEquals(0.0F, divergence.deepest(), EPSILON);
        assertEquals(1, divergence.planes());
        for (float axis : divergence.bounds()) {
            assertEquals(0.0F, axis, EPSILON);
        }
    }

    @Test
    void aFencePostWithAnArmFlattensThePostOntoTheArmsPlane() {
        List<ModelQuad> fence = new ArrayList<>(
                box(6 * SIXTEENTH, 0, 6 * SIXTEENTH, 10 * SIXTEENTH, 1, 10 * SIXTEENTH));
        fence.addAll(box(7 * SIXTEENTH, 12 * SIXTEENTH, 0, 9 * SIXTEENTH, 15 * SIXTEENTH, 6 * SIXTEENTH));

        ShapeDivergence divergence = measure(fence, bake(fence));

        assertEquals(6 * SIXTEENTH, divergence.depth()[Direction.NORTH.ordinal()], EPSILON);
        assertEquals(2, divergence.planes());
    }

    @Test
    void aStairBakedAsItsBaseStandsHalfABlockAboveItsLowerStep() {
        List<ModelQuad> stair = new ArrayList<>(box(0, 0, 0, 1, HALF, 1));
        stair.addAll(box(0, HALF, HALF, 1, 1, 1));

        ShapeDivergence divergence = measure(stair, bake(box(0, 0, 0, 1, 1, 1)));

        assertEquals(HALF, divergence.depth()[Direction.UP.ordinal()], EPSILON);
    }

    @Test
    void aFaceTheBakeDropsIsCountedAsLost() {
        List<ModelQuad> slab = box(0, 0, 0, 1, HALF, 1);

        ShapeDivergence divergence = measure(slab, BakedModel.empty());

        assertEquals(FaceMask.ALL, divergence.lostFaces());
    }

    @Test
    void aCrossBladeKeepsOneImageTexelPerSpriteColumn() {
        List<ModelQuad> cross = List.of(
                quad(new float[] {BLADE_TO, 0, BLADE_TO, BLADE_FROM, 0, BLADE_FROM, BLADE_FROM, 1, BLADE_FROM,
                        BLADE_TO, 1, BLADE_TO}),
                quad(new float[] {BLADE_FROM, 0, BLADE_TO, BLADE_TO, 0, BLADE_FROM, BLADE_TO, 1, BLADE_FROM,
                        BLADE_FROM, 1, BLADE_TO}));

        ShapeDivergence divergence = measure(cross, bake(cross));

        assertTrue(divergence.bladed());
        assertEquals(2, divergence.blades());
        assertEquals(ShapeDivergence.NO_BLADES, divergence.bladeScale(), EPSILON);
    }

    @Test
    void aQuadNeitherOnAFaceNorOnADiagonalCountsAsTilted() {
        ModelQuad ramp = quad(new float[] {0, 1, 0, 0, 0, 1, 1, 0, 1, 1, 1, 0});

        ShapeDivergence divergence = measure(List.of(ramp), bake(List.of(ramp)));

        assertEquals(1, divergence.tilted());
    }

    private BakedModel bake(List<ModelQuad> quads) {
        return rasterizer.rasterize(quads, OPAQUE_WHITE, NO_TINTS);
    }

    private static ShapeDivergence measure(List<ModelQuad> quads, BakedModel baked) {
        return ShapeDivergence.measure(quads, baked, SIXTEEN_COLUMNS);
    }

    private static List<ModelQuad> box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float[] min = {minX, minY, minZ};
        float[] max = {maxX, maxY, maxZ};
        List<ModelQuad> quads = new ArrayList<>();

        for (float[] unit : CUBE) {
            float[] positions = new float[unit.length];
            for (int index = 0; index < unit.length; index++) {
                int axis = index % ShapeDivergence.AXES;
                positions[index] = unit[index] == 0 ? min[axis] : max[axis];
            }

            quads.add(quad(positions));
        }

        return quads;
    }

    private static ModelQuad quad(float[] positions) {
        return new ModelQuad(new Vector3fc[] {
            new Vector3f(positions[0], positions[1], positions[2]),
            new Vector3f(positions[3], positions[4], positions[5]),
            new Vector3f(positions[6], positions[7], positions[8]),
            new Vector3f(positions[9], positions[10], positions[11])},
                FACE_UV.clone(), null, ModelQuad.NO_TINT, false, 0, Direction.UP);
    }
}
