package com.eminus.client.render.far;

import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.render.backend.DepthConvention;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.resources.Identifier;

import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class OpaquePass {
    public static final float ALPHA_CUTOUT = 0.5F;

    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "far_opaque");
    private static final String PASS_LABEL = "eminus-far-opaque";
    private static final Vector4fc CLEAR_COLOUR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

    private final RenderPipeline pipeline;

    private OpaquePass(RenderPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public static OpaquePass create(DepthConvention depth) {
        RenderSystem.assertOnRenderThread();
        return new OpaquePass(pipeline(depth));
    }

    public void draw(FarTarget target, GeometryArena arena, ModelPublisher models, GpuTextureView lightmap,
            GpuBufferSlice commands, int drawCount, GpuBuffer frame, GpuBuffer nearSections) {
        RenderSystem.assertOnRenderThread();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor(target))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            FarQuads.bind(pass, arena, models, lightmap, frame, nearSections);

            if (drawCount > 0) {
                pass.drawIndexedIndirect(commands, drawCount);
            }
        }
    }

    private static RenderPassDescriptor descriptor(FarTarget target) {
        return RenderPassDescriptor.builder(() -> PASS_LABEL)
                .withColorAttachment(target.colourView(), Optional.of(CLEAR_COLOUR))
                .withDepthAttachment(target.depthView(), OptionalDouble.empty())
                .withRenderArea(new RenderPass.RenderArea(0, 0, target.width(), target.height()))
                .build();
    }

    private static RenderPipeline pipeline(DepthConvention depth) {
        return FarQuads.pipeline(PIPELINE, ALPHA_CUTOUT)
                .withShaderDefine("FULL_COVERAGE")
                .withShaderDefine("NEAR_SECTIONS")
                .withColorTargetState(new ColorTargetState(Optional.empty(), FarTarget.COLOUR_FORMAT, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(new DepthStencilState(depth.compare(), true))
                .build();
    }
}
