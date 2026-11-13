package com.eminus.client.render.far;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Consumer;

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
import com.eminus.gpu.pipeline.DepthCompare;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;
import com.eminus.model.port.VariantDraw;
import com.eminus.render.backend.DepthConvention;

public final class TranslucentPass {
    public static final float ALPHA_CUTOUT = 0.1F;

    private static final Location FRONT_DEPTH_PIPELINE = new Location(Eminus.MODID, "far_translucent_front_depth");
    private static final Location SECOND_DEPTH_PIPELINE = new Location(Eminus.MODID, "far_translucent_second_depth");
    private static final Location BEHIND_PIPELINE = new Location(Eminus.MODID, "far_translucent_behind");
    private static final Location FRONT_PIPELINE = new Location(Eminus.MODID, "far_translucent");
    private static final Location DEPTH_PIPELINE = new Location(Eminus.MODID, "far_translucent_depth");
    private static final String FRONT_DEPTH_PASS_LABEL = "eminus-far-translucent-front-depth";
    private static final String SECOND_DEPTH_PASS_LABEL = "eminus-far-translucent-second-depth";
    private static final String BEHIND_PASS_LABEL = "eminus-far-translucent-behind";
    private static final String SECOND_PASS_LABEL = "eminus-far-translucent-second";
    private static final String FRONT_PASS_LABEL = "eminus-far-translucent";
    private static final String DEPTH_PASS_LABEL = "eminus-far-translucent-depth";

    private record Draw(GeometryArena arena, ModelPublisher models, Texture lightmap, Sampler atlas, Buffer commands,
            int firstCommand, int drawCount, Buffer frame, TexelView nearSections) {
    }

    private final Gpu gpu;
    private final DepthConvention depth;
    private final Pipeline frontDepth;
    private final Pipeline secondDepth;
    private final Pipeline behind;
    private final Pipeline front;
    private final Pipeline finalDepth;

    private TranslucentPass(Gpu gpu, DepthConvention depth, Pipeline frontDepth, Pipeline secondDepth,
            Pipeline behind, Pipeline front, Pipeline finalDepth) {
        this.gpu = gpu;
        this.depth = depth;
        this.frontDepth = frontDepth;
        this.secondDepth = secondDepth;
        this.behind = behind;
        this.front = front;
        this.finalDepth = finalDepth;
    }

    public static TranslucentPass create(Gpu gpu, DepthConvention depth, VariantDraw variantDraw) {
        gpu.assertRenderThread();
        Capabilities capabilities = gpu.capabilities();
        return new TranslucentPass(gpu, depth,
                gpu.pipeline(FarQuads.inFrontOfOpaque(pipeline(FRONT_DEPTH_PIPELINE, depth, capabilities, variantDraw))
                        .withColorTarget(FarTarget.COLOR_FORMAT, null, false)
                        .withDepthTest(depth.compare(), true)
                        .build()),
                gpu.pipeline(FarQuads.inFrontOfOpaque(FarQuads.behindFront(
                                pipeline(SECOND_DEPTH_PIPELINE, depth, capabilities, variantDraw)))
                        .withColorTarget(FarTarget.COLOR_FORMAT, null, false)
                        .withDepthTest(depth.compare(), true)
                        .build()),
                gpu.pipeline(FarQuads.behindFront(pipeline(BEHIND_PIPELINE, depth, capabilities, variantDraw))
                        .withColorTarget(FarTarget.COLOR_FORMAT, Blend.TRANSLUCENT, true)
                        .withDepthTest(depth.compare(), false)
                        .build()),
                gpu.pipeline(pipeline(FRONT_PIPELINE, depth, capabilities, variantDraw)
                        .withColorTarget(FarTarget.COLOR_FORMAT, Blend.TRANSLUCENT, true)
                        .withDepthTest(DepthCompare.EQUAL, false)
                        .build()),
                gpu.pipeline(pipeline(DEPTH_PIPELINE, depth, capabilities, variantDraw)
                        .withColorTarget(FarTarget.COLOR_FORMAT, null, false)
                        .withDepthTest(depth.compare(), true)
                        .build()));
    }

    public List<Pipeline> pipelines() {
        return List.of(frontDepth, secondDepth, behind, front, finalDepth);
    }

    public void draw(FarTarget target, GeometryArena arena, ModelPublisher models, Texture lightmap, Sampler atlas,
            Buffer commands, int firstCommand, int drawCount, Buffer frame, TexelView nearSections) {
        gpu.assertRenderThread();
        if (drawCount == 0) {
            return;
        }

        Draw draw = new Draw(arena, models, lightmap, atlas, commands, firstCommand, drawCount, frame, nearSections);
        draw(FRONT_DEPTH_PASS_LABEL, frontDepth, target, target.frontDepth(), OptionalDouble.of(depth.farthest()),
                draw, pass -> FarQuads.bindOpaqueDepth(pass, target.depth()));
        draw(SECOND_DEPTH_PASS_LABEL, secondDepth, target, target.secondDepth(),
                OptionalDouble.of(depth.farthest()), draw, pass -> {
                    FarQuads.bindOpaqueDepth(pass, target.depth());
                    FarQuads.bindFrontDepth(pass, target.frontDepth());
                });
        draw(BEHIND_PASS_LABEL, behind, target, target.depth(), OptionalDouble.empty(), draw,
                pass -> FarQuads.bindFrontDepth(pass, target.secondDepth()));
        draw(SECOND_PASS_LABEL, front, target, target.secondDepth(), OptionalDouble.empty(), draw, pass -> { });
        draw(FRONT_PASS_LABEL, front, target, target.frontDepth(), OptionalDouble.empty(), draw, pass -> { });
        draw(DEPTH_PASS_LABEL, finalDepth, target, target.depth(), OptionalDouble.empty(), draw, pass -> { });
    }

    private void draw(String label, Pipeline pipeline, FarTarget target, Texture depthAttachment,
            OptionalDouble clearDepth, Draw draw, Consumer<Pass> bindDepth) {
        try (Pass pass = gpu.pass(PassSpec.of(label, target.color(), null).withDepth(depthAttachment, clearDepth))) {
            pass.pipeline(pipeline);
            FarQuads.bind(pass, draw.arena(), draw.models(), draw.lightmap(), draw.atlas(), draw.frame(),
                    draw.nearSections());
            bindDepth.accept(pass);
            pass.drawIndexedIndirect(draw.commands(), draw.firstCommand(), draw.drawCount());
        }
    }

    private static PipelineSpec.Builder pipeline(Location location, DepthConvention depth, Capabilities capabilities,
            VariantDraw variantDraw) {
        return depth.define(FarQuads.pipeline(location, capabilities, variantDraw)
                .withDefine("NEAR_SECTIONS")
                .withDefine("TRANSLUCENT_PASS"));
    }
}
