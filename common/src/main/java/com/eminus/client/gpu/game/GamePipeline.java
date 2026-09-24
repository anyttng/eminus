package com.eminus.client.gpu.game;

import java.util.Optional;

import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.BindGroupLayouts;

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
    public boolean compiles() {
        return RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
    }
}
