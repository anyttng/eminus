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
    // The game clears its level depth to this before the sky, which writes none, so anything above it the near field drew.
    public static final float GAME_DEPTH_CLEARED = 0.0F;

    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "near_mask");
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/near_mask");
    private static final String PASS_LABEL = "eminus-near-mask";
    private static final String GAME_DEPTH = "GameDepth";
    private static final int VERTICES = 3;

    private final Gpu gpu;
    private final Pipeline pipeline;

    private NearMaskPass(Gpu gpu, Pipeline pipeline) {
        this.gpu = gpu;
        this.pipeline = pipeline;
    }

    public static NearMaskPass create(Gpu gpu, Format colourFormat) {
        gpu.assertRenderThread();
        return new NearMaskPass(gpu, gpu.pipeline(pipeline(colourFormat)));
    }

    // A GL render pass sizes its viewport from a colour attachment alone, so the far colour rides along unwritten.
    public void draw(Texture farDepth, Texture colour, Texture gameDepth) {
        gpu.assertRenderThread();

        try (Pass pass = gpu.pass(PassSpec.of(PASS_LABEL, colour, null)
                .withDepth(farDepth, OptionalDouble.of(DepthConvention.REVERSED_FARTHEST)))) {
            pass.pipeline(pipeline);
            pass.bind(GAME_DEPTH, gameDepth, Sampler.NEAREST);
            pass.draw(VERTICES);
        }
    }

    private static PipelineSpec pipeline(Format colourFormat) {
        return PipelineSpec.builder(PIPELINE, SHADER, SHADER)
                .withBinding(Binding.sampled(GAME_DEPTH))
                .withDefine("GAME_DEPTH_CLEARED", GAME_DEPTH_CLEARED)
                .withDefine("MASKED", (float) DepthConvention.REVERSED_NEAREST)
                .withColourTarget(colourFormat, null, false)
                .withDepthTest(DepthConvention.REVERSED_COMPARE, true)
                .build();
    }
}
