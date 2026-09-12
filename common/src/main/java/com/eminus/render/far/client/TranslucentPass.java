package com.eminus.render.far.client;

import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.render.arena.client.GeometryArena;
import com.eminus.render.backend.DepthConvention;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.resources.Identifier;

public final class TranslucentPass {
    public static final float ALPHA_CUTOUT = 0.1F;

    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "far_translucent");
    private static final String PASS_LABEL = "eminus-far-translucent";

    private final RenderPipeline pipeline;

    private TranslucentPass(RenderPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public static TranslucentPass create(DepthConvention depth) {
        RenderSystem.assertOnRenderThread();
        return new TranslucentPass(pipeline(depth));
    }

    public void draw(FarTarget target, GeometryArena arena, ModelPublisher models, GpuTextureView lightmap,
            GpuBufferSlice commands, int drawCount, GpuBuffer frame) {
        RenderSystem.assertOnRenderThread();
        if (drawCount == 0) {
            return;
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor(target))) {
            pass.setPipeline(pipeline);
            FarQuads.bind(pass, arena, models, lightmap, target.maskView(), frame);
            pass.drawIndirect(commands, drawCount);
        }
    }

    private static RenderPassDescriptor descriptor(FarTarget target) {
        return RenderPassDescriptor.create(() -> PASS_LABEL)
                .withColorAttachment(target.colourView(), Optional.empty())
                .withDepthAttachment(target.depthStencilView(), OptionalDouble.empty())
                .withRenderArea(new RenderPass.RenderArea(0, 0, target.width(), target.height()));
    }

    private static RenderPipeline pipeline(DepthConvention depth) {
        return FarQuads.pipeline(PIPELINE, ALPHA_CUTOUT)
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT),
                        FarTarget.COLOUR_FORMAT, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(new DepthStencilState(depth.compare(), true))
                .build();
    }
}
