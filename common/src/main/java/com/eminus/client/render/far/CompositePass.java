package com.eminus.client.render.far;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.OptionalDouble;
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
import com.eminus.gpu.pipeline.Blend;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.backend.DepthConvention;
import com.eminus.render.far.CompositeFog;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

public final class CompositePass implements AutoCloseable {
    public static final float DEPTH_BIAS = 4.0F / (1 << 24);
    public static final Location FULL_SCREEN_SHADER = new Location(Eminus.MODID, "core/far_composite");
    public static final Std140.Block BLOCK = Std140.block("Composite");

    private static final Std140.Member REPROJECT = BLOCK.add(Std140.Type.MAT4, "Reproject");
    private static final Std140.Member FAR_INVERSE = BLOCK.add(Std140.Type.MAT4, "FarInverse");
    private static final Std140.Member FOG_COLOUR = BLOCK.add(Std140.Type.VEC4, "FogColour");
    private static final Std140.Member GAME_FOG_START = BLOCK.add(Std140.Type.FLOAT, "GameFogStart");
    private static final Std140.Member GAME_FOG_END = BLOCK.add(Std140.Type.FLOAT, "GameFogEnd");
    private static final Std140.Member FOG_REACH = BLOCK.add(Std140.Type.FLOAT, "FogReach");
    private static final Std140.Member FOG_START = BLOCK.add(Std140.Type.FLOAT, "FogStart");
    private static final Std140.Member FOG_END = BLOCK.add(Std140.Type.FLOAT, "FogEnd");
    private static final Std140.Member FADE_START = BLOCK.add(Std140.Type.FLOAT, "FadeStart");
    private static final Std140.Member FADE_END = BLOCK.add(Std140.Type.FLOAT, "FadeEnd");
    private static final Std140.Member BIAS = BLOCK.add(Std140.Type.FLOAT, "DepthBias");
    private static final int SIZE = BLOCK.size();

    private static final Location PIPELINE = new Location(Eminus.MODID, "far_composite");
    private static final String PASS_LABEL = "eminus-far-composite";
    private static final String UNIFORM_LABEL = "eminus-composite";
    private static final String FAR_COLOUR = "FarColour";
    private static final String FAR_DEPTH = "FarDepth";
    private static final Set<BufferUsage> UNIFORM_USAGE = EnumSet.of(BufferUsage.UNIFORM, BufferUsage.COPY_DST);
    private static final long START_OF_BUFFER = 0L;
    private static final int VERTICES = 3;

    private final Gpu gpu;
    private final Pipeline pipeline;
    private final Buffer uniform;
    private final Matrix4f farInverse = new Matrix4f();
    private final Matrix4f reproject = new Matrix4f();

    private CompositePass(Gpu gpu, Pipeline pipeline, Buffer uniform) {
        this.gpu = gpu;
        this.pipeline = pipeline;
        this.uniform = uniform;
    }

    public static CompositePass create(Gpu gpu, DepthConvention depth) {
        gpu.assertRenderThread();
        return new CompositePass(gpu, gpu.pipeline(pipeline(depth, gpu.mainColour().format())),
                gpu.buffer(UNIFORM_LABEL, UNIFORM_USAGE, SIZE));
    }

    public Pipeline pipeline() {
        return pipeline;
    }

    public void draw(FarTarget far, Texture gameColour, Texture gameDepth, Matrix4fc farViewProjection,
            Matrix4fc gameViewProjection, CompositeFog fog, Vector4fc fogColour) {
        gpu.assertRenderThread();
        write(gameViewProjection, farViewProjection, fog, fogColour);

        try (Pass pass = gpu.pass(PassSpec.of(PASS_LABEL, gameColour, null)
                .withDepth(gameDepth, OptionalDouble.empty()))) {
            pass.pipeline(pipeline);
            pass.bind(BLOCK.name(), uniform);
            pass.bind(FAR_COLOUR, far.colour(), Sampler.NEAREST);
            pass.bind(FAR_DEPTH, far.depth(), Sampler.NEAREST);
            pass.draw(VERTICES);
        }
    }

    @Override
    public void close() {
        uniform.close();
    }

    private void write(Matrix4fc gameViewProjection, Matrix4fc farViewProjection, CompositeFog fog,
            Vector4fc fogColour) {
        FarProjection.reproject(gameViewProjection, farViewProjection, farInverse, reproject);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = BLOCK.into(stack.malloc(SIZE))
                    .putMat4(REPROJECT, reproject)
                    .putMat4(FAR_INVERSE, farInverse)
                    .putVec4(FOG_COLOUR, fogColour)
                    .putFloat(GAME_FOG_START, fog.gameFogStart())
                    .putFloat(GAME_FOG_END, fog.gameFogEnd())
                    .putFloat(FOG_REACH, fog.reach())
                    .putFloat(FOG_START, fog.fogStart())
                    .putFloat(FOG_END, fog.fogEnd())
                    .putFloat(FADE_START, fog.fadeStart())
                    .putFloat(FADE_END, fog.fadeEnd())
                    .putFloat(BIAS, DEPTH_BIAS)
                    .get();
            gpu.write(uniform, START_OF_BUFFER, written);
        }
    }

    private static PipelineSpec pipeline(DepthConvention depth, Format colourFormat) {
        return depth.define(PipelineSpec.builder(PIPELINE, FULL_SCREEN_SHADER, FULL_SCREEN_SHADER)
                        .withBinding(Binding.uniform(BLOCK.name()))
                        .withBinding(Binding.sampled(FAR_COLOUR))
                        .withBinding(Binding.sampled(FAR_DEPTH)))
                .withColourTarget(colourFormat, Blend.TRANSLUCENT_PREMULTIPLIED, true)
                .withDepthTest(depth.compare(), true)
                .build();
    }
}
