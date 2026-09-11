package com.eminus.handoff.client;

import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.handoff.CoverageSections;
import com.eminus.render.backend.DepthConvention;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;

public final class CoveragePass implements AutoCloseable {
    public static final int VERTICES_PER_SECTION = 36;
    // A section face on the plane the last visible section shares with an empty one z-fights the coverage without this.
    public static final float COVERAGE_MARGIN = 1.0F / 32.0F;

    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "coverage");
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/coverage");
    private static final String PASS_LABEL = "eminus-coverage";
    private static final String SECTIONS_LABEL = "eminus-coverage-sections";
    private static final int SECTIONS_USAGE = GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST;
    private static final int INSTANCES = 1;

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("FarFrame", UniformType.UNIFORM_BUFFER)
            .withUniform("Sections", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_SINT)
            .build();

    private final RenderPipeline pipeline;
    private GpuBuffer sections;

    private CoveragePass(RenderPipeline pipeline, GpuBuffer sections) {
        this.pipeline = pipeline;
        this.sections = sections;
    }

    public static CoveragePass create(GpuFormat colourFormat) {
        RenderSystem.assertOnRenderThread();
        return new CoveragePass(pipeline(colourFormat), sectionsBuffer(CoverageSections.INITIAL_CAPACITY));
    }

    public void draw(GpuTextureView coverage, GpuTextureView colour, int width, int height, GpuBuffer frame,
            CoverageSections covering) {
        RenderSystem.assertOnRenderThread();
        upload(covering);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(descriptor(coverage, colour, width, height))) {
            pass.setPipeline(pipeline);
            pass.setUniform("Globals", RenderSystem.getGlobalSettingsUniform());
            pass.setUniform("FarFrame", frame);
            pass.setUniform("Sections", sections);

            if (covering.count() > 0) {
                pass.draw(covering.count() * VERTICES_PER_SECTION, INSTANCES, 0, 0);
            }
        }
    }

    @Override
    public void close() {
        sections.close();
    }

    private void upload(CoverageSections covering) {
        long bytes = (long) covering.count() * CoverageSections.SECTION_BYTES;
        if (bytes == 0) {
            return;
        }

        if (bytes > sections.size()) {
            sections.close();
            sections = sectionsBuffer(covering.capacity());
        }

        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(sections.slice(0L, bytes), covering.buffer());
    }

    private static GpuBuffer sectionsBuffer(int capacity) {
        return RenderSystem.getDevice()
                .createBuffer(() -> SECTIONS_LABEL, SECTIONS_USAGE, (long) capacity * CoverageSections.SECTION_BYTES);
    }

    // A GL render pass sizes its viewport from a colour attachment alone, so the far colour rides along unwritten.
    private static RenderPassDescriptor descriptor(GpuTextureView coverage, GpuTextureView colour, int width,
            int height) {
        return RenderPassDescriptor.create(() -> PASS_LABEL)
                .withColorAttachment(colour, Optional.empty())
                .withDepthAttachment(coverage, OptionalDouble.of(DepthConvention.REVERSED_NEAREST))
                .withRenderArea(new RenderPass.RenderArea(0, 0, width, height));
    }

    private static RenderPipeline pipeline(GpuFormat colourFormat) {
        return RenderPipeline.builder()
                .withLocation(PIPELINE)
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(LAYOUT)
                .withShaderDefine("SECTION_SIZE", SectionPos.SECTION_SIZE)
                .withShaderDefine("COVERAGE_MARGIN", COVERAGE_MARGIN)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.empty(), colourFormat, ColorTargetState.WRITE_NONE))
                .withDepthStencilState(new DepthStencilState(DepthConvention.REVERSED_FARTHER_WINS, true))
                .withCull(false)
                .build();
    }
}
