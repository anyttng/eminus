package com.eminus.client.handoff;

import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.render.backend.DepthConvention;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.resources.Identifier;

public final class NearMaskPass {
    // The game clears its level depth to this before the sky, which writes none, so anything above it the near field drew.
    public static final float GAME_DEPTH_CLEARED = 0.0F;

    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "near_mask");
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/near_mask");
    private static final String PASS_LABEL = "eminus-near-mask";
    private static final int VERTICES = 3;
    private static final int INSTANCES = 1;

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("GameDepth", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    private final RenderPipeline pipeline;

    private NearMaskPass(RenderPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public static NearMaskPass create(GpuFormat colourFormat) {
        RenderSystem.assertOnRenderThread();
        return new NearMaskPass(pipeline(colourFormat));
    }

    public void draw(GpuTextureView farDepth, GpuTextureView colour, int width, int height,
            GpuTextureView gameDepth) {
        RenderSystem.assertOnRenderThread();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(descriptor(farDepth, colour, width, height))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setUniform("GameDepth", gameDepth,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(VERTICES, INSTANCES, 0, 0);
        }
    }

    // A GL render pass sizes its viewport from a colour attachment alone, so the far colour rides along unwritten.
    private static RenderPassDescriptor descriptor(GpuTextureView farDepth, GpuTextureView colour, int width,
            int height) {
        return RenderPassDescriptor.builder(() -> PASS_LABEL)
                .withColorAttachment(colour, Optional.empty())
                .withDepthAttachment(farDepth, OptionalDouble.of(DepthConvention.REVERSED_FARTHEST))
                .withRenderArea(new RenderPass.RenderArea(0, 0, width, height))
                .build();
    }

    private static RenderPipeline pipeline(GpuFormat colourFormat) {
        return RenderPipeline.builder()
                .withLocation(PIPELINE)
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withBindGroupLayout(LAYOUT)
                .withShaderDefine("GAME_DEPTH_CLEARED", GAME_DEPTH_CLEARED)
                .withShaderDefine("MASKED", (float) DepthConvention.REVERSED_NEAREST)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.empty(), colourFormat, ColorTargetState.WRITE_NONE))
                .withDepthStencilState(new DepthStencilState(DepthConvention.REVERSED_COMPARE, true))
                .withCull(false)
                .build();
    }
}
