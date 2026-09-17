package com.eminus.client.render.backend;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.render.backend.BackendLimitation;
import com.eminus.render.backend.BackendSupport;
import com.eminus.render.backend.DepthConvention;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.device.DeviceFeatures;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.resources.Identifier;

import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class BackendCheck {
    public static final List<GpuFormat> DEPTH_STENCIL_FORMATS =
            List.of(GpuFormat.D32_FLOAT_S8_UINT, GpuFormat.D24_UNORM_S8_UINT);

    private static final Identifier PROBE_PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "depth_probe");
    private static final Identifier PROBE_SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/depth_probe");
    private static final String COLOUR_LABEL = "eminus-probe-colour";
    private static final String DEPTH_LABEL = "eminus-probe-depth";
    private static final String PASS_LABEL = "eminus-probe-pass";
    private static final GpuFormat COLOUR_FORMAT = GpuFormat.RGBA8_UNORM;
    private static final Vector4fc CLEAR_COLOUR = new Vector4f();
    private static final int TARGET_USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT;
    private static final int PROBE_SIDE = 1;
    private static final int PROBE_LAYERS = 1;
    private static final int PROBE_MIPS = 1;
    private static final int PROBE_VERTICES = 3;
    private static final int PROBE_INSTANCES = 1;

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

        for (GpuFormat format : DEPTH_STENCIL_FORMATS) {
            if (draws(device, probe, format)) {
                Eminus.LOGGER.info("Backend accepted: depth-stencil format {}, depth range {}", format, depth.range());
                return BackendSupport.accepted(format, depth);
            }
        }

        return refuse(BackendLimitation.DEPTH_STENCIL_TARGET, depth);
    }

    private static BackendSupport refuse(BackendLimitation limitation, DepthConvention depth) {
        Eminus.LOGGER.warn("Renderer disabled: {}", limitation.reason());
        return BackendSupport.refused(limitation, depth);
    }

    private static boolean draws(GpuDevice device, RenderPipeline probe, GpuFormat format) {
        try (GpuTexture colour =
                        device.createTexture(COLOUR_LABEL, TARGET_USAGE, COLOUR_FORMAT, PROBE_SIDE, PROBE_SIDE, PROBE_LAYERS, PROBE_MIPS);
                GpuTexture depthStencil =
                        device.createTexture(DEPTH_LABEL, TARGET_USAGE, format, PROBE_SIDE, PROBE_SIDE, PROBE_LAYERS, PROBE_MIPS);
                GpuTextureView colourView = device.createTextureView(colour);
                GpuTextureView depthStencilView = device.createTextureView(depthStencil);
                RenderPass pass = device.createCommandEncoder().createRenderPass(descriptor(colourView, depthStencilView))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(probe));
            pass.draw(PROBE_VERTICES, PROBE_INSTANCES, 0, 0);
            return true;
        } catch (RuntimeException refused) {
            Eminus.LOGGER.info("Depth-stencil format {} refused: {}", format, refused.getMessage());
            return false;
        }
    }

    private static RenderPassDescriptor descriptor(GpuTextureView colour, GpuTextureView depthStencil) {
        return RenderPassDescriptor.builder(() -> PASS_LABEL)
                .withColorAttachment(colour, Optional.of(CLEAR_COLOUR))
                .withDepthAttachment(depthStencil, OptionalDouble.of(DepthConvention.REVERSED_FARTHEST))
                .withRenderArea(new RenderPass.RenderArea(0, 0, PROBE_SIDE, PROBE_SIDE))
                .build();
    }

    private static RenderPipeline probePipeline(DepthConvention depth) {
        return RenderPipeline.builder()
                .withLocation(PROBE_PIPELINE)
                .withVertexShader(PROBE_SHADER)
                .withFragmentShader(PROBE_SHADER)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.empty(), COLOUR_FORMAT, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(new DepthStencilState(depth.compare(), true))
                .withCull(false)
                .build();
    }
}
