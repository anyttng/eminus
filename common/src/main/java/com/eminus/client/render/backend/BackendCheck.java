package com.eminus.client.render.backend;

import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.render.backend.BackendLimitation;
import com.eminus.render.backend.BackendSupport;
import com.eminus.render.backend.DepthConvention;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.device.DeviceFeatures;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.resources.Identifier;

import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

public final class BackendCheck {
    public static final GpuFormat DEPTH_FORMAT = GpuFormat.D32_FLOAT;

    private static final Identifier PROBE_PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "depth_probe");
    private static final Identifier PROBE_SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/depth_probe");
    private static final String COLOUR_LABEL = "eminus-probe-colour";
    private static final String DEPTH_LABEL = "eminus-probe-depth";
    private static final String PASS_LABEL = "eminus-probe-pass";
    private static final String UNIFORM_LABEL = "eminus-probe-uniform";
    private static final String PROBE_UNIFORM = "Probe";
    private static final GpuFormat COLOUR_FORMAT = GpuFormat.RGBA8_UNORM;
    private static final Vector4fc CLEAR_COLOUR = new Vector4f();
    private static final int TARGET_USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT;
    private static final int PROBE_SIDE = 1;
    private static final int PROBE_LAYERS = 1;
    private static final int PROBE_MIPS = 1;
    private static final int PROBE_VERTICES = 3;
    private static final int PROBE_INSTANCES = 1;
    private static final float PROBE_DEPTH = 0.5F;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putFloat().get();

    private static final BindGroupLayout PROBE_LAYOUT = BindGroupLayout.builder()
            .withUniform(PROBE_UNIFORM, UniformType.UNIFORM_BUFFER)
            .build();

    private BackendCheck() {
    }

    public static BackendSupport run(long arenaBytes) {
        RenderSystem.assertOnRenderThread();
        GpuDevice device = RenderSystem.getDevice();
        DepthConvention depth = DepthConvention.of(device.getDeviceInfo());

        if (arenaBytes <= 0 || arenaBytes > device.getDeviceInfo().limits().maxMemoryAllocationSize()) {
            return refuse(BackendLimitation.ARENA_MEMORY, depth);
        }

        DeviceFeatures features = device.getDeviceInfo().features();
        if (!features.drawIndirect() || !features.multiDrawIndirect()) {
            return refuse(BackendLimitation.DRAW_INDIRECT, depth);
        }

        RenderPipeline probe = probePipeline(depth);
        if (RenderSystem.getCompiledPipelineNullable(probe) == null) {
            return refuse(BackendLimitation.FRAGMENT_DEPTH, depth);
        }

        if (!draws(device, probe, DEPTH_FORMAT)) {
            return refuse(BackendLimitation.DEPTH_TARGET, depth);
        }

        Eminus.LOGGER.info("Backend accepted: depth format {}, depth range {}", DEPTH_FORMAT, depth.range());
        return BackendSupport.accepted(DEPTH_FORMAT, depth);
    }

    private static BackendSupport refuse(BackendLimitation limitation, DepthConvention depth) {
        Eminus.LOGGER.warn("Renderer disabled: {}", limitation.reason());
        return BackendSupport.refused(limitation, depth);
    }

    private static boolean draws(GpuDevice device, RenderPipeline probe, GpuFormat format) {
        try (GpuBuffer uniform = probeUniform(device);
                GpuTexture colour =
                        device.createTexture(COLOUR_LABEL, TARGET_USAGE, COLOUR_FORMAT, PROBE_SIDE, PROBE_SIDE, PROBE_LAYERS, PROBE_MIPS);
                GpuTexture depth =
                        device.createTexture(DEPTH_LABEL, TARGET_USAGE, format, PROBE_SIDE, PROBE_SIDE, PROBE_LAYERS, PROBE_MIPS);
                GpuTextureView colourView = device.createTextureView(colour);
                GpuTextureView depthView = device.createTextureView(depth);
                RenderPass pass = device.createCommandEncoder().createRenderPass(descriptor(colourView, depthView))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(probe));
            pass.setUniform(PROBE_UNIFORM, uniform);
            pass.draw(PROBE_VERTICES, PROBE_INSTANCES, 0, 0);
            return true;
        } catch (RuntimeException refused) {
            Eminus.LOGGER.info("Depth format {} refused: {}", format, refused.getMessage());
            return false;
        }
    }

    private static GpuBuffer probeUniform(GpuDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return device.createBuffer(() -> UNIFORM_LABEL, GpuBuffer.USAGE_UNIFORM,
                    Std140Builder.onStack(stack, UNIFORM_SIZE).putFloat(PROBE_DEPTH).get());
        }
    }

    private static RenderPassDescriptor descriptor(GpuTextureView colour, GpuTextureView depth) {
        return RenderPassDescriptor.builder(() -> PASS_LABEL)
                .withColorAttachment(colour, Optional.of(CLEAR_COLOUR))
                .withDepthAttachment(depth, OptionalDouble.of(DepthConvention.REVERSED_FARTHEST))
                .withRenderArea(new RenderPass.RenderArea(0, 0, PROBE_SIDE, PROBE_SIDE))
                .build();
    }

    private static RenderPipeline probePipeline(DepthConvention depth) {
        return RenderPipeline.builder()
                .withLocation(PROBE_PIPELINE)
                .withVertexShader(PROBE_SHADER)
                .withFragmentShader(PROBE_SHADER)
                .withBindGroupLayout(PROBE_LAYOUT)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.empty(), COLOUR_FORMAT, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(new DepthStencilState(depth.compare(), true))
                .withCull(false)
                .build();
    }
}
