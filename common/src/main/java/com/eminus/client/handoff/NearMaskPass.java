package com.eminus.client.handoff;

import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.render.backend.DepthConvention;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;

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
            .withSampler("GameDepth")
            .build();

    private final RenderPipeline pipeline;

    private NearMaskPass(RenderPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public static NearMaskPass create(GpuFormat colourFormat) {
        RenderSystem.assertOnRenderThread();
        return new NearMaskPass(pipeline(colourFormat));
    }

    public void draw(GpuTextureView mask, GpuTextureView colour, int width, int height, GpuTextureView gameDepth) {
        RenderSystem.assertOnRenderThread();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(descriptor(mask, colour, width, height))) {
            pass.setPipeline(pipeline);
            pass.bindTexture("GameDepth", gameDepth,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(VERTICES, INSTANCES, 0, 0);
        }
    }

    // A GL render pass sizes its viewport from a colour attachment alone, so the far colour rides along unwritten.
    private static RenderPassDescriptor descriptor(GpuTextureView mask, GpuTextureView colour, int width, int height) {
        return RenderPassDescriptor.create(() -> PASS_LABEL)
                .withColorAttachment(colour, Optional.empty())
                .withDepthAttachment(mask, OptionalDouble.of(DepthConvention.REVERSED_NEAREST))
                .withRenderArea(new RenderPass.RenderArea(0, 0, width, height));
    }

    private static RenderPipeline pipeline(GpuFormat colourFormat) {
        return RenderPipeline.builder()
                .withLocation(PIPELINE)
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withBindGroupLayout(LAYOUT)
                .withShaderDefine("GAME_DEPTH_CLEARED", GAME_DEPTH_CLEARED)
                .withShaderDefine("MASKED", (float) DepthConvention.REVERSED_FARTHEST)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.empty(), colourFormat, ColorTargetState.WRITE_NONE))
                .withDepthStencilState(new DepthStencilState(DepthConvention.REVERSED_FARTHER_WINS, true))
                .withCull(false)
                .build();
    }
}
