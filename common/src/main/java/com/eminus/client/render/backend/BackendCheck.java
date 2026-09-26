package com.eminus.client.render.backend;

import java.util.EnumSet;
import java.util.OptionalDouble;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.gpu.Capabilities;
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
import com.eminus.gpu.texture.Texture;
import com.eminus.gpu.texture.TextureUsage;
import com.eminus.render.backend.BackendLimitation;
import com.eminus.render.backend.BackendSupport;
import com.eminus.render.backend.DepthConvention;

import net.minecraft.resources.Identifier;

import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

public final class BackendCheck {
    public static final Format DEPTH_FORMAT = Format.D32_FLOAT;

    private static final Identifier PROBE_PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "depth_probe");
    private static final Identifier PROBE_SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/depth_probe");
    private static final String COLOUR_LABEL = "eminus-probe-colour";
    private static final String DEPTH_LABEL = "eminus-probe-depth";
    private static final String PASS_LABEL = "eminus-probe-pass";
    private static final String UNIFORM_LABEL = "eminus-probe-uniform";
    private static final String PROBE_UNIFORM = "Probe";
    private static final Format COLOUR_FORMAT = Format.RGBA8_UNORM;
    private static final Vector4fc CLEAR_COLOUR = new Vector4f();
    private static final Set<TextureUsage> TARGET_USAGE = EnumSet.of(TextureUsage.ATTACHMENT);
    private static final Set<BufferUsage> UNIFORM_USAGE = EnumSet.of(BufferUsage.UNIFORM);
    private static final int PROBE_SIDE = 1;
    private static final int PROBE_MIPS = 1;
    private static final int PROBE_VERTICES = 3;
    private static final float PROBE_DEPTH = 0.5F;
    private static final int UNIFORM_SIZE = Std140.size().putFloat().get();

    private BackendCheck() {
    }

    public static BackendSupport run(Gpu gpu, long arenaBytes) {
        gpu.assertRenderThread();
        Capabilities capabilities = gpu.capabilities();
        DepthConvention depth = DepthConvention.of(capabilities.depthZeroToOne(), capabilities.depthReversed());

        if (arenaBytes <= 0 || arenaBytes > capabilities.maxAllocationBytes()) {
            return refuse(BackendLimitation.ARENA_MEMORY, depth);
        }

        if (!capabilities.drawIndirect() || !capabilities.multiDrawIndirect()) {
            return refuse(BackendLimitation.DRAW_INDIRECT, depth);
        }

        Pipeline probe = gpu.pipeline(probePipeline(depth));
        if (!probe.compiles()) {
            return refuse(BackendLimitation.FRAGMENT_DEPTH, depth);
        }

        if (!draws(gpu, probe, DEPTH_FORMAT, depth)) {
            return refuse(BackendLimitation.DEPTH_TARGET, depth);
        }

        Eminus.LOGGER.info("Backend accepted: depth format {}, depth range {}, depth {}", DEPTH_FORMAT, depth.range(),
                depth.direction());
        return BackendSupport.accepted(DEPTH_FORMAT, depth);
    }

    private static BackendSupport refuse(BackendLimitation limitation, DepthConvention depth) {
        Eminus.LOGGER.warn("Renderer disabled: {}", limitation.reason());
        return BackendSupport.refused(limitation, depth);
    }

    private static boolean draws(Gpu gpu, Pipeline probe, Format format, DepthConvention convention) {
        try (Buffer uniform = probeUniform(gpu);
                Texture colour = gpu.texture(COLOUR_LABEL, TARGET_USAGE, COLOUR_FORMAT, PROBE_SIDE, PROBE_SIDE,
                        PROBE_MIPS);
                Texture depth = gpu.texture(DEPTH_LABEL, TARGET_USAGE, format, PROBE_SIDE, PROBE_SIDE, PROBE_MIPS);
                Pass pass = gpu.pass(PassSpec.of(PASS_LABEL, colour, CLEAR_COLOUR)
                        .withDepth(depth, OptionalDouble.of(convention.farthest())))) {
            pass.pipeline(probe);
            pass.bind(PROBE_UNIFORM, uniform);
            pass.draw(PROBE_VERTICES);
            return true;
        } catch (RuntimeException refused) {
            Eminus.LOGGER.info("Depth format {} refused: {}", format, refused.getMessage());
            return false;
        }
    }

    private static Buffer probeUniform(Gpu gpu) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return gpu.buffer(UNIFORM_LABEL, UNIFORM_USAGE,
                    Std140.into(stack.malloc(UNIFORM_SIZE)).putFloat(PROBE_DEPTH).get());
        }
    }

    private static PipelineSpec probePipeline(DepthConvention depth) {
        return PipelineSpec.builder(PROBE_PIPELINE, PROBE_SHADER, PROBE_SHADER)
                .withBinding(Binding.uniform(PROBE_UNIFORM))
                .withColourTarget(COLOUR_FORMAT, null, true)
                .withDepthTest(depth.compare(), true)
                .build();
    }
}
