package com.eminus.render.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.eminus.gpu.Format;
import com.eminus.gpu.pipeline.DepthCompare;
import com.eminus.gpu.pipeline.PipelineSpec;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

class DepthConventionTest {
    private static final boolean ZERO_TO_ONE = true;
    private static final boolean MINUS_ONE_TO_ONE = false;
    private static final boolean REVERSED = true;
    private static final boolean FORWARD = false;
    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath("eminus", "test");

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

    private static List<PipelineSpec.Define> defines(DepthConvention depth) {
        return depth.define(PipelineSpec.builder(PIPELINE, PIPELINE, PIPELINE))
                .withColourTarget(Format.RGBA8_UNORM, null, true)
                .build()
                .defines();
    }
}
