package com.eminus.client.handoff;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.OptionalDouble;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.client.render.far.CompositePass;
import com.eminus.client.render.far.FarProjection;
import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.Location;
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

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

public final class NearMaskPass implements AutoCloseable {
    public static final Std140.Block BLOCK = Std140.block("Mask");

    private static final Std140.Member GAME_TO_FAR = BLOCK.add(Std140.Type.MAT4, "GameToFar");
    private static final Std140.Member BIAS = BLOCK.add(Std140.Type.FLOAT, "DepthBias");
    private static final int SIZE = BLOCK.size();

    private static final Location PIPELINE = new Location(Eminus.MODID, "near_mask");
    private static final Location FRAGMENT_SHADER = new Location(Eminus.MODID, "core/near_mask");
    private static final String PASS_LABEL = "eminus-near-mask";
    private static final String UNIFORM_LABEL = "eminus-near-mask";
    private static final String GAME_DEPTH = "GameDepth";
    private static final Set<BufferUsage> UNIFORM_USAGE = EnumSet.of(BufferUsage.UNIFORM, BufferUsage.COPY_DST);
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
            pass.bind(BLOCK.name(), uniform);
            pass.bind(GAME_DEPTH, gameDepth, Sampler.NEAREST);
            pass.draw(VERTICES);
        }
    }

    @Override
    public void close() {
        uniform.close();
    }

    private void write(Matrix4fc farViewProjection, Matrix4fc gameViewProjection) {
        FarProjection.gameToFar(farViewProjection, gameViewProjection, gameInverse, gameToFar);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = BLOCK.into(stack.malloc(SIZE))
                    .putMat4(GAME_TO_FAR, gameToFar)
                    .putFloat(BIAS, depthBias)
                    .get();
            gpu.write(uniform, START_OF_BUFFER, written);
        }
    }

    private static PipelineSpec pipeline(Format colourFormat, DepthConvention depth) {
        return depth.define(PipelineSpec.builder(PIPELINE, CompositePass.FULL_SCREEN_SHADER, FRAGMENT_SHADER)
                        .withBinding(Binding.uniform(BLOCK.name()))
                        .withBinding(Binding.sampled(GAME_DEPTH)))
                .withColourTarget(colourFormat, null, false)
                .withDepthTest(depth.compare(), true)
                .build();
    }
}
