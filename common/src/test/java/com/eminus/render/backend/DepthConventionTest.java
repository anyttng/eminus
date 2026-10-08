package com.eminus.render.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.eminus.gpu.Format;
import com.eminus.gpu.Location;
import com.eminus.gpu.pipeline.DepthCompare;
import com.eminus.gpu.pipeline.PipelineSpec;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class DepthConventionTest {
    private static final boolean ZERO_TO_ONE = true;
    private static final boolean MINUS_ONE_TO_ONE = false;
    private static final boolean REVERSED = true;
    private static final boolean FORWARD = false;
    private static final Location PIPELINE = new Location("eminus", "test");
    private static final float FOV = (float) Math.toRadians(70.0);
    private static final float ASPECT = 16.0F / 9.0F;
    private static final float NEAR = 16.0F;
    private static final float FAR = 48_000.0F;
    private static final float HALF = 0.5F;
    private static final float TOLERANCE = 1.0e-4F;
    private static final float[] DISTANCES = {NEAR, 100.0F, 2_000.0F, 40_000.0F};

    @Test
    void reversedDepthKeepsTheNearerFragmentOnTheGreaterValue() {
        DepthConvention depth = DepthConvention.of(ZERO_TO_ONE, REVERSED);

        assertEquals(DepthCompare.GREATER_OR_EQUAL, depth.compare());
        assertEquals(DepthCompare.LESS_OR_EQUAL, depth.fartherWins());
        assertEquals(0.0, depth.farthest());
        assertEquals(1.0, depth.nearest());
    }

    @Test
    void forwardDepthKeepsTheNearerFragmentOnTheLesserValue() {
        DepthConvention depth = DepthConvention.of(MINUS_ONE_TO_ONE, FORWARD);

        assertEquals(DepthCompare.LESS_OR_EQUAL, depth.compare());
        assertEquals(DepthCompare.GREATER_OR_EQUAL, depth.fartherWins());
        assertEquals(1.0, depth.farthest());
        assertEquals(0.0, depth.nearest());
    }

    @Test
    void theShaderLearnsTheDirectionAndTheRangeFromTheDefines() {
        assertEquals(List.of(new PipelineSpec.Define("FARTHEST", 0.0F), new PipelineSpec.Define("NEAREST", 1.0F),
                        new PipelineSpec.Define("DEPTH_ZERO_TO_ONE", null),
                        new PipelineSpec.Define("DEPTH_REVERSED", null)),
                defines(DepthConvention.of(ZERO_TO_ONE, REVERSED)));
        assertEquals(List.of(new PipelineSpec.Define("FARTHEST", 1.0F), new PipelineSpec.Define("NEAREST", 0.0F)),
                defines(DepthConvention.of(MINUS_ONE_TO_ONE, FORWARD)));
    }

    @Test
    void everyConventionComesBackAsTheForwardMinusOneToOneProjection() {
        Matrix4f forward = new Matrix4f().perspective(FOV, ASPECT, NEAR, FAR);
        Matrix4f reversedZeroToOne = new Matrix4f().m22(-HALF).m32(HALF).mul(forward);
        Matrix4f reversedMinusOneToOne = new Matrix4f().m22(-1.0F).mul(forward);
        Matrix4f forwardZeroToOne = new Matrix4f().m22(HALF).m32(HALF).mul(forward);

        assertSameClip(forward, DepthConvention.of(ZERO_TO_ONE, REVERSED).forward(reversedZeroToOne, new Matrix4f()));
        assertSameClip(forward,
                DepthConvention.of(MINUS_ONE_TO_ONE, REVERSED).forward(reversedMinusOneToOne, new Matrix4f()));
        assertSameClip(forward, DepthConvention.of(ZERO_TO_ONE, FORWARD).forward(forwardZeroToOne, new Matrix4f()));
        assertSameClip(forward, DepthConvention.of(MINUS_ONE_TO_ONE, FORWARD).forward(forward, new Matrix4f()));
    }

    private static void assertSameClip(Matrix4fc expected, Matrix4fc actual) {
        for (float distance : DISTANCES) {
            Vector4f point = new Vector4f(1.0F, 2.0F, -distance, 1.0F);
            Vector4f wanted = expected.transform(point, new Vector4f());
            Vector4f got = actual.transform(point, new Vector4f());
            assertEquals(wanted.z / wanted.w, got.z / got.w, TOLERANCE, "depth at " + distance);
            assertEquals(wanted.w, got.w, TOLERANCE, "w at " + distance);
        }
    }

    private static List<PipelineSpec.Define> defines(DepthConvention depth) {
        return depth.define(PipelineSpec.builder(PIPELINE, PIPELINE, PIPELINE))
                .withColourTarget(Format.RGBA8_UNORM, null, true)
                .build()
                .defines();
    }
}
