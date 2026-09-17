package com.eminus.client.render.far;

import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.render.backend.DepthConvention;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;

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
            GpuBufferSlice commands, int drawCount, GpuBuffer frame, GpuBuffer nearSections) {
        RenderSystem.assertOnRenderThread();
        if (drawCount == 0) {
            return;
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor(target))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            FarQuads.bind(pass, arena, models, lightmap, target.maskView(), frame, nearSections);
            pass.drawIndirect(commands, drawCount);
        }
    }

    private static RenderPassDescriptor descriptor(FarTarget target) {
        return RenderPassDescriptor.builder(() -> PASS_LABEL)
                .withColorAttachment(target.colourView(), Optional.empty())
                .withDepthAttachment(target.depthStencilView(), OptionalDouble.empty())
                .withRenderArea(new RenderPass.RenderArea(0, 0, target.width(), target.height()))
                .build();
    }

    private static RenderPipeline pipeline(DepthConvention depth) {
        return FarQuads.pipeline(PIPELINE, ALPHA_CUTOUT)
                .withShaderDefine("NEAR_SECTIONS")
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT),
                        FarTarget.COLOUR_FORMAT, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(new DepthStencilState(depth.compare(), true))
                .build();
    }
}
