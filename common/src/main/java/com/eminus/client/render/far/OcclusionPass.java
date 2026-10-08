package com.eminus.client.render.far;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.Location;
import com.eminus.gpu.Std140;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.Blend;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.backend.DepthConvention;

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
    public static final Std140.Block BLOCK = Std140.block("Occlusion");

    private static final Std140.Member FAR_VIEW_PROJECTION = BLOCK.add(Std140.Type.MAT4, "FarViewProjection");
    private static final Std140.Member FAR_INVERSE = BLOCK.add(Std140.Type.MAT4, "FarInverse");
    private static final Std140.Member GAME_INVERSE = BLOCK.add(Std140.Type.MAT4, "GameInverse");
    private static final Std140.Member GAME_TO_FAR = BLOCK.add(Std140.Type.MAT4, "GameToFar");
    private static final Std140.Member FOCAL_PIXELS = BLOCK.add(Std140.Type.FLOAT, "FocalPixels");
    private static final Std140.Member BIAS = BLOCK.add(Std140.Type.FLOAT, "DepthBias");
    private static final int SIZE = BLOCK.size();

    private static final Location PIPELINE = new Location(Eminus.MODID, "far_occlusion");
    private static final Location FRAGMENT_SHADER = new Location(Eminus.MODID,
            "core/far_occlusion");
    private static final String PASS_LABEL = "eminus-far-occlusion";
    private static final String UNIFORM_LABEL = "eminus-occlusion";
    private static final String FAR_DEPTH = "FarDepth";
    private static final String GAME_DEPTH = "GameDepth";
    private static final Set<BufferUsage> UNIFORM_USAGE = EnumSet.of(BufferUsage.UNIFORM, BufferUsage.COPY_DST);
    private static final long START_OF_BUFFER = 0L;
    private static final int VERTICES = 3;
    private static final float HALF = 0.5F;

    private final Gpu gpu;
    private final Pipeline pipeline;
    private final Buffer uniform;
    private final Matrix4f farInverse = new Matrix4f();
    private final Matrix4f gameInverse = new Matrix4f();
    private final Matrix4f gameToFar = new Matrix4f();

    private OcclusionPass(Gpu gpu, Pipeline pipeline, Buffer uniform) {
        this.gpu = gpu;
        this.pipeline = pipeline;
        this.uniform = uniform;
    }

    public static OcclusionPass create(Gpu gpu, DepthConvention depth) {
        gpu.assertRenderThread();
        return new OcclusionPass(gpu, gpu.pipeline(pipeline(depth)), gpu.buffer(UNIFORM_LABEL, UNIFORM_USAGE, SIZE));
    }

    public Pipeline pipeline() {
        return pipeline;
    }

    public void draw(FarTarget far, Texture gameDepth, Matrix4fc farViewProjection, Matrix4fc gameViewProjection) {
        gpu.assertRenderThread();
        write(farViewProjection, gameViewProjection, far.height());

        try (Pass pass = gpu.pass(PassSpec.of(PASS_LABEL, far.colour(), null))) {
            pass.pipeline(pipeline);
            pass.bind(BLOCK.name(), uniform);
            pass.bind(FAR_DEPTH, far.depth(), Sampler.NEAREST);
            pass.bind(GAME_DEPTH, gameDepth, Sampler.NEAREST);
            pass.draw(VERTICES);
        }
    }

    @Override
    public void close() {
        uniform.close();
    }

    private void write(Matrix4fc farViewProjection, Matrix4fc gameViewProjection, int height) {
        farViewProjection.invert(farInverse);
        FarProjection.gameToFar(farViewProjection, gameViewProjection, gameInverse, gameToFar);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = BLOCK.into(stack.malloc(SIZE))
                    .putMat4(FAR_VIEW_PROJECTION, farViewProjection)
                    .putMat4(FAR_INVERSE, farInverse)
                    .putMat4(GAME_INVERSE, gameInverse)
                    .putMat4(GAME_TO_FAR, gameToFar)
                    .putFloat(FOCAL_PIXELS, focalPixels(farViewProjection, height))
                    .putFloat(BIAS, CompositePass.DEPTH_BIAS)
                    .get();
            gpu.write(uniform, START_OF_BUFFER, written);
        }
    }

    // A view rotation keeps a row's length, so the second row of the view-projection carries the projection's vertical scale.
    private static float focalPixels(Matrix4fc viewProjection, int height) {
        double scale = Math.sqrt(viewProjection.m01() * viewProjection.m01()
                + viewProjection.m11() * viewProjection.m11()
                + viewProjection.m21() * viewProjection.m21());
        return (float) scale * height * HALF;
    }

    private static PipelineSpec pipeline(DepthConvention depth) {
        return depth.define(PipelineSpec.builder(PIPELINE, CompositePass.FULL_SCREEN_SHADER, FRAGMENT_SHADER)
                        .withBinding(Binding.uniform(BLOCK.name()))
                        .withBinding(Binding.sampled(FAR_DEPTH))
                        .withBinding(Binding.sampled(GAME_DEPTH)))
                .withDefine("SAMPLES", SAMPLES)
                .withDefine("RADIUS", RADIUS)
                .withDefine("PIXEL_RADIUS", PIXEL_RADIUS)
                .withDefine("STRENGTH", STRENGTH)
                .withDefine("MIN_BIAS", MIN_BIAS)
                .withDefine("BIAS_PER_SQUARED_BLOCK", BIAS_PER_SQUARED_BLOCK)
                .withColourTarget(FarTarget.COLOUR_FORMAT, Blend.MULTIPLY, true)
                .build();
    }
}
