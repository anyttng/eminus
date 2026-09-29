package com.eminus.client.handoff;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.OptionalDouble;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.Std140;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.backend.DepthConvention;

import net.minecraft.resources.Identifier;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

public final class NearMaskPass implements AutoCloseable {
    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "near_mask");
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/near_mask");
    private static final String PASS_LABEL = "eminus-near-mask";
    private static final String UNIFORM_LABEL = "eminus-near-mask";
    private static final String MASK = "Mask";
    private static final String GAME_DEPTH = "GameDepth";
    private static final Set<BufferUsage> UNIFORM_USAGE = EnumSet.of(BufferUsage.UNIFORM, BufferUsage.COPY_DST);
    private static final int SIZE = Std140.size().putMat4f().putFloat().get();
    private static final long START_OF_BUFFER = 0L;
    private static final int VERTICES = 3;

    private final Gpu gpu;
    private final Pipeline pipeline;
    private final Buffer uniform;
    private final DepthConvention depth;
    private final float depthBias;
    private final Matrix4f gameInverse = new Matrix4f();
    private final Matrix4f gameToFar = new Matrix4f();

    private NearMaskPass(Gpu gpu, Pipeline pipeline, Buffer uniform, DepthConvention depth, float depthBias) {
        this.gpu = gpu;
        this.pipeline = pipeline;
        this.uniform = uniform;
        this.depth = depth;
        this.depthBias = depthBias;
    }

    public static NearMaskPass create(Gpu gpu, Format colourFormat, DepthConvention depth, float depthBias) {
        gpu.assertRenderThread();
        return new NearMaskPass(gpu, gpu.pipeline(pipeline(colourFormat, depth)),
                gpu.buffer(UNIFORM_LABEL, UNIFORM_USAGE, SIZE), depth, depthBias);
    }

    // A GL render pass sizes its viewport from a colour attachment alone, so the far colour rides along unwritten.
    public Pipeline pipeline() {
        return pipeline;
    }

    public void draw(Texture farDepth, Texture colour, Texture gameDepth, Matrix4fc farViewProjection,
            Matrix4fc gameViewProjection) {
        gpu.assertRenderThread();
        write(farViewProjection, gameViewProjection);

        try (Pass pass = gpu.pass(PassSpec.of(PASS_LABEL, colour, null)
                .withDepth(farDepth, OptionalDouble.of(depth.farthest())))) {
            pass.pipeline(pipeline);
            pass.bind(MASK, uniform);
            pass.bind(GAME_DEPTH, gameDepth, Sampler.NEAREST);
            pass.draw(VERTICES);
        }
    }

    @Override
    public void close() {
        uniform.close();
    }

    private void write(Matrix4fc farViewProjection, Matrix4fc gameViewProjection) {
        gameViewProjection.invert(gameInverse);
        farViewProjection.mul(gameInverse, gameToFar);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = Std140.into(stack.malloc(SIZE))
                    .putMat4f(gameToFar)
                    .putFloat(depthBias)
                    .get();
            gpu.write(uniform, START_OF_BUFFER, written);
        }
    }

    private static PipelineSpec pipeline(Format colourFormat, DepthConvention depth) {
        return depth.define(PipelineSpec.builder(PIPELINE, SHADER, SHADER)
                        .withBinding(Binding.uniform(MASK))
                        .withBinding(Binding.sampled(GAME_DEPTH)))
                .withColourTarget(colourFormat, null, false)
                .withDepthTest(depth.compare(), true)
                .build();
    }
}
