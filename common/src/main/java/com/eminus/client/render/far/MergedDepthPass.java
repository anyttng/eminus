package com.eminus.client.render.far;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Set;

import com.eminus.Eminus;
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

public final class MergedDepthPass implements AutoCloseable {
    public static final Format FORMAT = Format.R32_FLOAT;
    public static final float NEAR = 0.05F;
    public static final float FAR = FarProjection.FAR;

    public static Matrix4f projection(Matrix4fc forwardGameProjection, Matrix4f target) {
        return target.set(forwardGameProjection)
                .m22((FAR + NEAR) / (NEAR - FAR))
                .m32(CLIP_SPAN * FAR * NEAR / (NEAR - FAR));
    }

    private static final float CLIP_SPAN = 2.0F;

    private static final Std140.Block BLOCK = Std140.block("Merge");
    private static final Std140.Member GAME_INVERSE = BLOCK.add(Std140.Type.MAT4, "GameInverse");
    private static final Std140.Member FAR_INVERSE = BLOCK.add(Std140.Type.MAT4, "FarInverse");
    private static final Std140.Member MERGED_NEAR = BLOCK.add(Std140.Type.FLOAT, "MergedNear");
    private static final Std140.Member MERGED_FAR = BLOCK.add(Std140.Type.FLOAT, "MergedFar");
    private static final Std140.Member HAND_DEPTH = BLOCK.add(Std140.Type.FLOAT, "HandDepth");
    private static final int SIZE = BLOCK.size();

    private static final Location PIPELINE = new Location(Eminus.MODID, "far_merge");
    private static final Location FRAGMENT_SHADER = new Location(Eminus.MODID, "core/far_merge");
    private static final String PASS_LABEL = "eminus-far-merge";
    private static final String UNIFORM_LABEL = "eminus-far-merge";
    private static final String GAME_DEPTH = "GameDepth";
    private static final String FAR_DEPTH = "FarDepth";
    private static final Set<BufferUsage> UNIFORM_USAGE = EnumSet.of(BufferUsage.UNIFORM, BufferUsage.COPY_DST);
    private static final long START_OF_BUFFER = 0L;
    private static final int VERTICES = 3;

    private final Gpu gpu;
    private final Pipeline pipeline;
    private final Buffer uniform;

    private MergedDepthPass(Gpu gpu, Pipeline pipeline, Buffer uniform) {
        this.gpu = gpu;
        this.pipeline = pipeline;
        this.uniform = uniform;
    }

    public static MergedDepthPass create(Gpu gpu, DepthConvention depth) {
        gpu.assertRenderThread();
        return new MergedDepthPass(gpu, gpu.pipeline(pipeline(depth)), gpu.buffer(UNIFORM_LABEL, UNIFORM_USAGE, SIZE));
    }

    public void draw(Texture merged, Texture gameDepth, Texture farDepth, Matrix4fc gameInverse,
            Matrix4fc farInverse, float handDepth) {
        gpu.assertRenderThread();
        write(gameInverse, farInverse, handDepth);

        try (Pass pass = gpu.pass(PassSpec.of(PASS_LABEL, merged, null))) {
            pass.pipeline(pipeline);
            pass.bind(BLOCK.name(), uniform);
            pass.bind(GAME_DEPTH, gameDepth, Sampler.NEAREST);
            pass.bind(FAR_DEPTH, farDepth, Sampler.NEAREST);
            pass.draw(VERTICES);
        }
    }

    @Override
    public void close() {
        uniform.close();
    }

    private void write(Matrix4fc gameInverse, Matrix4fc farInverse, float handDepth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = BLOCK.into(stack.malloc(SIZE))
                    .putMat4(GAME_INVERSE, gameInverse)
                    .putMat4(FAR_INVERSE, farInverse)
                    .putFloat(MERGED_NEAR, NEAR)
                    .putFloat(MERGED_FAR, FAR)
                    .putFloat(HAND_DEPTH, handDepth)
                    .get();
            gpu.write(uniform, START_OF_BUFFER, written);
        }
    }

    private static PipelineSpec pipeline(DepthConvention depth) {
        return depth.define(PipelineSpec.builder(PIPELINE, CompositePass.FULL_SCREEN_SHADER, FRAGMENT_SHADER)
                        .withBinding(Binding.uniform(BLOCK.name()))
                        .withBinding(Binding.sampled(GAME_DEPTH))
                        .withBinding(Binding.sampled(FAR_DEPTH)))
                .withColorTarget(FORMAT, null, true)
                .build();
    }
}
