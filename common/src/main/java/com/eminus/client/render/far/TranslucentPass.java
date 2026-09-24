package com.eminus.client.render.far;

import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Blend;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.backend.DepthConvention;

import net.minecraft.resources.Identifier;

public final class TranslucentPass {
    public static final float ALPHA_CUTOUT = 0.1F;

    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "far_translucent");
    private static final String PASS_LABEL = "eminus-far-translucent";

    private final Gpu gpu;
    private final Pipeline pipeline;

    private TranslucentPass(Gpu gpu, Pipeline pipeline) {
        this.gpu = gpu;
        this.pipeline = pipeline;
    }

    public static TranslucentPass create(Gpu gpu, DepthConvention depth) {
        gpu.assertRenderThread();
        return new TranslucentPass(gpu, gpu.pipeline(pipeline(depth)));
    }

    public void draw(FarTarget target, GeometryArena arena, ModelPublisher models, Texture lightmap, Buffer commands,
            int firstCommand, int drawCount, Buffer frame, TexelView nearSections) {
        gpu.assertRenderThread();
        if (drawCount == 0) {
            return;
        }

        try (Pass pass = gpu.pass(PassSpec.of(PASS_LABEL, target.colour(), null)
                .withDepth(target.depth(), OptionalDouble.empty()))) {
            pass.pipeline(pipeline);
            FarQuads.bind(pass, arena, models, lightmap, frame, nearSections);
            pass.drawIndexedIndirect(commands, firstCommand, drawCount);
        }
    }

    private static PipelineSpec pipeline(DepthConvention depth) {
        return FarQuads.pipeline(PIPELINE, ALPHA_CUTOUT)
                .withDefine("NEAR_SECTIONS")
                .withColourTarget(FarTarget.COLOUR_FORMAT, Blend.TRANSLUCENT, true)
                .withDepthTest(depth.compare(), true)
                .build();
    }
}
