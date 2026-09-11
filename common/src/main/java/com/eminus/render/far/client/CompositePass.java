package com.eminus.render.far.client;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.render.backend.DepthConvention;
import com.eminus.render.far.CompositeFog;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.resources.Identifier;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

public final class CompositePass implements AutoCloseable {
    public static final float DEPTH_BIAS = 4.0F / (1 << 24);

    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "far_composite");
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/far_composite");
    private static final String PASS_LABEL = "eminus-far-composite";
    private static final String UNIFORM_LABEL = "eminus-composite";
    private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
    private static final int SIZE = new Std140SizeCalculator()
            .putMat4f().putMat4f().putVec4()
            .putFloat().putFloat().putFloat().putFloat().putFloat()
            .get();
    private static final int VERTICES = 3;
    private static final int INSTANCES = 1;

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("Composite", UniformType.UNIFORM_BUFFER)
            .withSampler("FarColour")
            .withSampler("FarDepth")
            .build();

    private final RenderPipeline pipeline;
    private final GpuBuffer uniform;
    private final Matrix4f farInverse = new Matrix4f();
    private final Matrix4f reproject = new Matrix4f();

    private CompositePass(RenderPipeline pipeline, GpuBuffer uniform) {
        this.pipeline = pipeline;
        this.uniform = uniform;
    }

    public static CompositePass create(DepthConvention depth) {
        RenderSystem.assertOnRenderThread();
        GpuBuffer uniform = RenderSystem.getDevice().createBuffer(() -> UNIFORM_LABEL, UNIFORM_USAGE, SIZE);
        return new CompositePass(pipeline(depth), uniform);
    }

    public void draw(FarTarget far, RenderTarget game, Matrix4fc farViewProjection, Matrix4fc gameViewProjection,
            CompositeFog fog, Vector4fc fogColour) {
        RenderSystem.assertOnRenderThread();
        write(gameViewProjection, farViewProjection, fog, fogColour);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor(game))) {
            pass.setPipeline(pipeline);
            pass.setUniform("Composite", uniform);
            pass.bindTexture("FarColour", far.colourView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.bindTexture("FarDepth", far.depthStencilView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(VERTICES, INSTANCES, 0, 0);
        }
    }

    @Override
    public void close() {
        uniform.close();
    }

    private void write(Matrix4fc gameViewProjection, Matrix4fc farViewProjection, CompositeFog fog,
            Vector4fc fogColour) {
        farViewProjection.invert(farInverse);
        gameViewProjection.mul(farInverse, reproject);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = Std140Builder.onStack(stack, SIZE)
                    .putMat4f(reproject)
                    .putMat4f(farInverse)
                    .putVec4(fogColour)
                    .putFloat(fog.fogStart())
                    .putFloat(fog.fogEnd())
                    .putFloat(fog.fadeStart())
                    .putFloat(fog.fadeEnd())
                    .putFloat(DEPTH_BIAS)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniform.slice(), written);
        }
    }

    private static RenderPassDescriptor descriptor(RenderTarget game) {
        return RenderPassDescriptor.create(() -> PASS_LABEL)
                .withColorAttachment(game.getColorTextureView(), Optional.empty())
                .withDepthAttachment(game.getDepthTextureView(), OptionalDouble.empty())
                .withRenderArea(new RenderPass.RenderArea(0, 0, game.width, game.height));
    }

    private static RenderPipeline pipeline(DepthConvention depth) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(PIPELINE)
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withBindGroupLayout(LAYOUT)
                .withShaderDefine("FARTHEST", (float) DepthConvention.REVERSED_FARTHEST)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(depth.compare(), true))
                .withCull(false);

        return depth.zeroToOne() ? builder.withShaderDefine("DEPTH_ZERO_TO_ONE").build() : builder.build();
    }
}
