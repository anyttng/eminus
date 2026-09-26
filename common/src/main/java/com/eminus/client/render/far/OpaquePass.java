package com.eminus.client.render.far;

import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.gpu.Capabilities;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Texture;
import com.eminus.model.port.VariantDraw;
import com.eminus.render.backend.DepthConvention;

import net.minecraft.resources.Identifier;

import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class OpaquePass {
    public static final float ALPHA_CUTOUT = 0.5F;

    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "far_opaque");
    private static final String PASS_LABEL = "eminus-far-opaque";
    private static final Vector4fc CLEAR_COLOUR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

    private final Gpu gpu;
    private final Pipeline pipeline;

    private OpaquePass(Gpu gpu, Pipeline pipeline) {
        this.gpu = gpu;
        this.pipeline = pipeline;
    }

    public static OpaquePass create(Gpu gpu, DepthConvention depth, VariantDraw variantDraw) {
        gpu.assertRenderThread();
        return new OpaquePass(gpu, gpu.pipeline(pipeline(depth, gpu.capabilities(), variantDraw)));
    }

    public Pipeline pipeline() {
        return pipeline;
    }

    public void draw(FarTarget target, GeometryArena arena, ModelPublisher models, Texture lightmap, Buffer commands,
            int firstCommand, int drawCount, Buffer frame, TexelView nearSections) {
        gpu.assertRenderThread();

        try (Pass pass = gpu.pass(PassSpec.of(PASS_LABEL, target.colour(), CLEAR_COLOUR)
                .withDepth(target.depth(), OptionalDouble.empty()))) {
            pass.pipeline(pipeline);
            FarQuads.bind(pass, arena, models, lightmap, frame, nearSections);

            if (drawCount > 0) {
                pass.drawIndexedIndirect(commands, firstCommand, drawCount);
            }
        }
    }

    private static PipelineSpec pipeline(DepthConvention depth, Capabilities capabilities, VariantDraw variantDraw) {
        return FarQuads.pipeline(PIPELINE, ALPHA_CUTOUT, capabilities, variantDraw)
                .withDefine("FULL_COVERAGE")
                .withDefine("NEAR_SECTIONS")
                .withColourTarget(FarTarget.COLOUR_FORMAT, null, true)
                .withDepthTest(depth.compare(), true)
                .build();
    }
}
