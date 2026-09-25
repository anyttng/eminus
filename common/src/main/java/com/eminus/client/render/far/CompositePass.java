package com.eminus.client.render.far;

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
import com.eminus.gpu.pipeline.Blend;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.backend.DepthConvention;
import com.eminus.render.far.CompositeFog;

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
    private static final String COMPOSITE = "Composite";
    private static final String FAR_COLOUR = "FarColour";
    private static final String FAR_DEPTH = "FarDepth";
    private static final Set<BufferUsage> UNIFORM_USAGE = EnumSet.of(BufferUsage.UNIFORM, BufferUsage.COPY_DST);
    private static final int SIZE = Std140.size()
            .putMat4f().putMat4f().putVec4()
            .putFloat().putFloat().putFloat()
            .putFloat().putFloat().putFloat().putFloat().putFloat()
            .get();
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
            pass.bind(COMPOSITE, uniform);
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
            ByteBuffer written = Std140.into(stack.malloc(SIZE))
                    .putMat4f(reproject)
                    .putMat4f(farInverse)
                    .putVec4(fogColour)
                    .putFloat(fog.gameFogStart())
                    .putFloat(fog.gameFogEnd())
                    .putFloat(fog.reach())
                    .putFloat(fog.fogStart())
                    .putFloat(fog.fogEnd())
                    .putFloat(fog.fadeStart())
                    .putFloat(fog.fadeEnd())
                    .putFloat(DEPTH_BIAS)
                    .get();
            gpu.write(uniform, START_OF_BUFFER, written);
        }
    }

    private static PipelineSpec pipeline(DepthConvention depth, Format colourFormat) {
        PipelineSpec.Builder builder = PipelineSpec.builder(PIPELINE, SHADER, SHADER)
                .withBinding(Binding.uniform(COMPOSITE))
                .withBinding(Binding.sampled(FAR_COLOUR))
                .withBinding(Binding.sampled(FAR_DEPTH))
                .withDefine("FARTHEST", (float) DepthConvention.REVERSED_FARTHEST)
                .withDefine("NEAREST", (float) DepthConvention.REVERSED_NEAREST)
                .withColourTarget(colourFormat, Blend.TRANSLUCENT_PREMULTIPLIED, true)
                .withDepthTest(depth.compare(), true);

        return depth.zeroToOne() ? builder.withDefine("DEPTH_ZERO_TO_ONE").build() : builder.build();
    }
}
