package com.eminus.client.render.far;

import java.util.List;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.client.model.ModelPublisher;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.gpu.Capabilities;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.Location;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Blend;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;
import com.eminus.model.port.VariantDraw;
import com.eminus.render.backend.DepthConvention;

import org.jspecify.annotations.Nullable;

public final class TranslucentPass {
    public static final float ALPHA_CUTOUT = 0.1F;

    private static final Location PIPELINE = new Location(Eminus.MODID, "far_translucent");
    private static final Location DEPTH_PIPELINE = new Location(Eminus.MODID, "far_translucent_depth");
    private static final String PASS_LABEL = "eminus-far-translucent";
    private static final String DEPTH_PASS_LABEL = "eminus-far-translucent-depth";

    private final Gpu gpu;
    private final Pipeline color;
    private final Pipeline depth;

    private TranslucentPass(Gpu gpu, Pipeline color, Pipeline depth) {
        this.gpu = gpu;
        this.color = color;
        this.depth = depth;
    }

    public static TranslucentPass create(Gpu gpu, DepthConvention depth, VariantDraw variantDraw) {
        gpu.assertRenderThread();
        Capabilities capabilities = gpu.capabilities();
        return new TranslucentPass(gpu,
                gpu.pipeline(pipeline(PIPELINE, Blend.TRANSLUCENT, true, false, depth, capabilities, variantDraw)),
                gpu.pipeline(pipeline(DEPTH_PIPELINE, null, false, true, depth, capabilities, variantDraw)));
    }

    public List<Pipeline> pipelines() {
        return List.of(color, depth);
    }

    public void draw(FarTarget target, GeometryArena arena, ModelPublisher models, Texture lightmap, Sampler atlas,
            Buffer commands, int firstCommand, int drawCount, Buffer frame, TexelView nearSections) {
        gpu.assertRenderThread();
        if (drawCount == 0) {
            return;
        }

        draw(PASS_LABEL, color, target, arena, models, lightmap, atlas, commands, firstCommand, drawCount, frame,
                nearSections);
        draw(DEPTH_PASS_LABEL, depth, target, arena, models, lightmap, atlas, commands, firstCommand, drawCount,
                frame, nearSections);
    }

    private void draw(String label, Pipeline pipeline, FarTarget target, GeometryArena arena, ModelPublisher models,
            Texture lightmap, Sampler atlas, Buffer commands, int firstCommand, int drawCount, Buffer frame,
            TexelView nearSections) {
        try (Pass pass = gpu.pass(PassSpec.of(label, target.color(), null)
                .withDepth(target.depth(), OptionalDouble.empty()))) {
            pass.pipeline(pipeline);
            FarQuads.bind(pass, arena, models, lightmap, atlas, frame, nearSections);
            pass.drawIndexedIndirect(commands, firstCommand, drawCount);
        }
    }

    private static PipelineSpec pipeline(Location location, @Nullable Blend blend, boolean writesColor,
            boolean writesDepth, DepthConvention depth, Capabilities capabilities, VariantDraw variantDraw) {
        return FarQuads.pipeline(location, ALPHA_CUTOUT, capabilities, variantDraw)
                .withDefine("NEAR_SECTIONS")
                .withColorTarget(FarTarget.COLOR_FORMAT, blend, writesColor)
                .withDepthTest(depth.compare(), writesDepth)
                .build();
    }
}
