package com.eminus.client.handoff;

import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.backend.DepthConvention;

import net.minecraft.resources.Identifier;

public final class NearMaskPass {
    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "near_mask");
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/near_mask");
    private static final String PASS_LABEL = "eminus-near-mask";
    private static final String GAME_DEPTH = "GameDepth";
    private static final int VERTICES = 3;

    private final Gpu gpu;
    private final Pipeline pipeline;
    private final DepthConvention depth;

    private NearMaskPass(Gpu gpu, Pipeline pipeline, DepthConvention depth) {
        this.gpu = gpu;
        this.pipeline = pipeline;
        this.depth = depth;
    }

    public static NearMaskPass create(Gpu gpu, Format colourFormat, DepthConvention depth) {
        gpu.assertRenderThread();
        return new NearMaskPass(gpu, gpu.pipeline(pipeline(colourFormat, depth)), depth);
    }

    // A GL render pass sizes its viewport from a colour attachment alone, so the far colour rides along unwritten.
    public Pipeline pipeline() {
        return pipeline;
    }

    public void draw(Texture farDepth, Texture colour, Texture gameDepth) {
        gpu.assertRenderThread();

        try (Pass pass = gpu.pass(PassSpec.of(PASS_LABEL, colour, null)
                .withDepth(farDepth, OptionalDouble.of(depth.farthest())))) {
            pass.pipeline(pipeline);
            pass.bind(GAME_DEPTH, gameDepth, Sampler.NEAREST);
            pass.draw(VERTICES);
        }
    }

    private static PipelineSpec pipeline(Format colourFormat, DepthConvention depth) {
        return depth.define(PipelineSpec.builder(PIPELINE, SHADER, SHADER)
                        .withBinding(Binding.sampled(GAME_DEPTH)))
                .withColourTarget(colourFormat, null, false)
                .withDepthTest(depth.compare(), true)
                .build();
    }
}
