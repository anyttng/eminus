package com.eminus.client.gpu.game;

import java.util.Optional;

import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.mixin.GlDeviceAccessor;
import com.eminus.mixin.VulkanDeviceAccessor;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

record GamePipeline(RenderPipeline pipeline) implements Pipeline {
    static GamePipeline of(PipelineSpec spec) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(spec.location())
                .withVertexShader(spec.vertexShader())
                .withFragmentShader(spec.fragmentShader());

        if (spec.gameGlobals()) {
            builder.withBindGroupLayout(BindGroupLayouts.GLOBALS);
        }

        if (!spec.bindings().isEmpty()) {
            builder.withBindGroupLayout(layout(spec));
        }

        for (PipelineSpec.Define define : spec.defines()) {
            switch (define.value()) {
                case null -> builder.withShaderDefine(define.name());
                case Integer value -> builder.withShaderDefine(define.name(), value);
                case Float value -> builder.withShaderDefine(define.name(), value);
                default -> throw new IllegalArgumentException("Define " + define.name() + " is neither int nor float");
            }
        }

        PipelineSpec.ColourTarget colour = spec.colour();
        builder.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(
                        Optional.ofNullable(colour.blend()).map(GameTypes::blend),
                        GameTypes.format(colour.format()),
                        colour.writes() ? ColorTargetState.WRITE_ALL : ColorTargetState.WRITE_NONE))
                .withCull(false);

        PipelineSpec.DepthTest depth = spec.depth();
        if (depth != null) {
            builder.withDepthStencilState(new DepthStencilState(GameTypes.compare(depth.compare()), depth.writes()));
        }

        return new GamePipeline(builder.build());
    }

    private static BindGroupLayout layout(PipelineSpec spec) {
        BindGroupLayout.Builder layout = BindGroupLayout.builder();
        for (Binding binding : spec.bindings()) {
            if (binding.kind() == Binding.Kind.SAMPLED) {
                layout.withSampler(binding.name());
            } else if (binding.format() == null) {
                layout.withUniform(binding.name(), GameTypes.uniformType(binding.kind()));
            } else {
                layout.withUniform(binding.name(), GameTypes.uniformType(binding.kind()),
                        GameTypes.format(binding.format()));
            }
        }
        return layout.build();
    }

    @Override
    public Identifier location() {
        return pipeline.getLocation();
    }

    @Override
    public boolean compiles() {
        return RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
    }

    void release(GpuDeviceBackend backend) {
        if (backend instanceof VulkanDevice vulkan) {
            VulkanRenderPipeline compiled = ((VulkanDeviceAccessor) vulkan).eminus$pipelineCache().remove(pipeline);
            if (compiled != null) {
                vulkan.createCommandEncoder().queueForDestroy(compiled);
            }
        } else if (backend instanceof GlDeviceAccessor gl) {
            GlRenderPipeline compiled = gl.eminus$pipelineCache().remove(pipeline);
            if (compiled != null && compiled.program() != GlProgram.INVALID_PROGRAM) {
                compiled.program().close();
            }
        }
    }
}
