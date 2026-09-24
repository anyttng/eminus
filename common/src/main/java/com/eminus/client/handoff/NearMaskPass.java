package com.eminus.client.handoff;

import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.client.gpu.game.GameTypes;
import com.eminus.gpu.Format;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.backend.DepthConvention;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

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

    public static NearMaskPass create(Format colourFormat) {
        RenderSystem.assertOnRenderThread();
        return new NearMaskPass(pipeline(colourFormat));
    }

    public void draw(Texture farDepth, Texture colour, Texture gameDepth) {
        RenderSystem.assertOnRenderThread();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(descriptor(farDepth, colour))) {
            pass.setPipeline(pipeline);
            pass.bindTexture("GameDepth", GameTypes.view(gameDepth),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(VERTICES, INSTANCES, 0, 0);
        }
    }

    // A GL render pass sizes its viewport from a colour attachment alone, so the far colour rides along unwritten.
    private static RenderPassDescriptor descriptor(Texture farDepth, Texture colour) {
        return RenderPassDescriptor.create(() -> PASS_LABEL)
                .withColorAttachment(GameTypes.view(colour), Optional.empty())
                .withDepthAttachment(GameTypes.view(farDepth), OptionalDouble.of(DepthConvention.REVERSED_FARTHEST))
                .withRenderArea(new RenderPass.RenderArea(0, 0, colour.width(), colour.height()));
    }

    private static RenderPipeline pipeline(Format colourFormat) {
        return RenderPipeline.builder()
                .withLocation(PIPELINE)
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withBindGroupLayout(LAYOUT)
                .withShaderDefine("GAME_DEPTH_CLEARED", GAME_DEPTH_CLEARED)
                .withShaderDefine("MASKED", (float) DepthConvention.REVERSED_NEAREST)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.empty(), GameTypes.format(colourFormat),
                        ColorTargetState.WRITE_NONE))
                .withDepthStencilState(new DepthStencilState(GameTypes.compare(DepthConvention.REVERSED_COMPARE), true))
                .withCull(false)
                .build();
    }
}
