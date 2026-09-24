package com.eminus.client.render.far;

import java.nio.ByteBuffer;
import java.util.Optional;

import com.eminus.Eminus;
import com.eminus.client.gpu.game.GameTypes;
import com.eminus.client.handoff.NearMaskPass;
import com.eminus.render.backend.DepthConvention;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.resources.Identifier;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

public final class OcclusionPass implements AutoCloseable {
    public static final int SAMPLES = 12;
    public static final float RADIUS = 1.0F;
    public static final float PIXEL_RADIUS = 6.0F;
    public static final float STRENGTH = 0.4F;
    public static final float MIN_BIAS = 0.02F;
    public static final float BIAS_PER_SQUARED_BLOCK = 6.0E-8F;

    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "far_occlusion");
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/far_composite");
    private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID,
            "core/far_occlusion");
    private static final String PASS_LABEL = "eminus-far-occlusion";
    private static final String UNIFORM_LABEL = "eminus-occlusion";
    private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
    private static final int SIZE = new Std140SizeCalculator()
            .putMat4f().putMat4f().putMat4f()
            .putFloat()
            .get();
    private static final int VERTICES = 3;
    private static final int INSTANCES = 1;
    private static final float HALF = 0.5F;
    private static final BlendFunction MULTIPLY = new BlendFunction(BlendFactor.ZERO, BlendFactor.SRC_COLOR,
            BlendFactor.ZERO, BlendFactor.ONE);

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("Occlusion", UniformType.UNIFORM_BUFFER)
            .withUniform("FarDepth", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("GameDepth", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    private final RenderPipeline pipeline;
    private final GpuBuffer uniform;
    private final Matrix4f farInverse = new Matrix4f();
    private final Matrix4f gameInverse = new Matrix4f();

    private OcclusionPass(RenderPipeline pipeline, GpuBuffer uniform) {
        this.pipeline = pipeline;
        this.uniform = uniform;
    }

    public static OcclusionPass create(DepthConvention depth) {
        RenderSystem.assertOnRenderThread();
        GpuBuffer uniform = RenderSystem.getDevice().createBuffer(() -> UNIFORM_LABEL, UNIFORM_USAGE, SIZE);
        return new OcclusionPass(pipeline(depth), uniform);
    }

    public void draw(FarTarget far, RenderTarget game, Matrix4fc farViewProjection, Matrix4fc gameViewProjection) {
        RenderSystem.assertOnRenderThread();
        write(farViewProjection, gameViewProjection, far.height());

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor(far))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setUniform("Occlusion", uniform);
            pass.setUniform("FarDepth", GameTypes.view(far.depth()),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("GameDepth", game.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(VERTICES, INSTANCES, 0, 0);
        }
    }

    @Override
    public void close() {
        uniform.close();
    }

    private void write(Matrix4fc farViewProjection, Matrix4fc gameViewProjection, int height) {
        farViewProjection.invert(farInverse);
        gameViewProjection.invert(gameInverse);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = Std140Builder.onStack(stack, SIZE)
                    .putMat4f(farViewProjection)
                    .putMat4f(farInverse)
                    .putMat4f(gameInverse)
                    .putFloat(focalPixels(farViewProjection, height))
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniform.slice(), written);
        }
    }

    // A view rotation keeps a row's length, so the second row of the view-projection carries the projection's vertical scale.
    private static float focalPixels(Matrix4fc viewProjection, int height) {
        double scale = Math.sqrt(viewProjection.m01() * viewProjection.m01()
                + viewProjection.m11() * viewProjection.m11()
                + viewProjection.m21() * viewProjection.m21());
        return (float) scale * height * HALF;
    }

    private static RenderPassDescriptor descriptor(FarTarget far) {
        return RenderPassDescriptor.builder(() -> PASS_LABEL)
                .withColorAttachment(GameTypes.view(far.colour()), Optional.empty())
                .withRenderArea(new RenderPass.RenderArea(0, 0, far.width(), far.height()))
                .build();
    }

    private static RenderPipeline pipeline(DepthConvention depth) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(PIPELINE)
                .withVertexShader(VERTEX_SHADER)
                .withFragmentShader(FRAGMENT_SHADER)
                .withBindGroupLayout(LAYOUT)
                .withShaderDefine("FARTHEST", (float) DepthConvention.REVERSED_FARTHEST)
                .withShaderDefine("NEAREST", (float) DepthConvention.REVERSED_NEAREST)
                .withShaderDefine("GAME_DEPTH_CLEARED", NearMaskPass.GAME_DEPTH_CLEARED)
                .withShaderDefine("SAMPLES", SAMPLES)
                .withShaderDefine("RADIUS", RADIUS)
                .withShaderDefine("PIXEL_RADIUS", PIXEL_RADIUS)
                .withShaderDefine("STRENGTH", STRENGTH)
                .withShaderDefine("MIN_BIAS", MIN_BIAS)
                .withShaderDefine("BIAS_PER_SQUARED_BLOCK", BIAS_PER_SQUARED_BLOCK)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.of(MULTIPLY),
                        GameTypes.format(FarTarget.COLOUR_FORMAT), ColorTargetState.WRITE_ALL))
                .withCull(false);

        return depth.zeroToOne() ? builder.withShaderDefine("DEPTH_ZERO_TO_ONE").build() : builder.build();
    }
}
