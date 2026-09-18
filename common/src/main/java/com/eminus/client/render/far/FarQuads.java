package com.eminus.client.render.far;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.handoff.NearSections;
import com.eminus.mesh.Quad;
import com.eminus.model.BakedModel;
import com.eminus.render.arena.ArenaAllocator;
import com.eminus.client.render.arena.GeometryArena;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

final class FarQuads {
    static final float SHADE_BLADE = 1.0F;

    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/far_quads");

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("FarFrame", UniformType.UNIFORM_BUFFER)
            .withUniform("Quads", UniformType.TEXEL_BUFFER, GpuFormat.RG32_UINT)
            .withUniform("MeshRecords", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT)
            .withUniform("ModelRecords", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
            .withUniform("ModelVariants", UniformType.TEXEL_BUFFER, GpuFormat.RG32_UINT)
            .withUniform("NearSections", UniformType.TEXEL_BUFFER, GpuFormat.R32_UINT)
            .withUniform("Atlas", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("TintMask", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Lightmap", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("NearMask", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    static RenderPipeline.Builder pipeline(Identifier location, float alphaCutout) {
        return RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(LAYOUT)
                .withShaderDefine("QUADS_PER_BLOCK", ArenaAllocator.QUADS_PER_BLOCK)
                .withShaderDefine("VOXELS_PER_SIDE", DetailLevel.VOXELS_PER_SIDE)
                .withShaderDefine("MIN_HORIZONTAL", CellKey.MIN_HORIZONTAL)
                .withShaderDefine("MIN_VERTICAL", CellKey.MIN_VERTICAL)
                .withShaderDefine("MODEL_FACES", BakedModel.FACE_COUNT)
                .withShaderDefine("FIRST_BLADE_FACE", Quad.FIRST_BLADE_FACE)
                .withShaderDefine("FACE_SIDE", BakedModel.FACE_SIDE)
                .withShaderDefine("MAX_VARIANT_REJECTIONS", BakedModel.MAX_VARIANT_REJECTIONS)
                .withShaderDefine("ALPHA_CUTOUT", alphaCutout)
                .withShaderDefine("SHADE_BLADE", SHADE_BLADE)
                .withShaderDefine("NEAR_SECTION_BLOCKS", NearSections.SECTION_BLOCKS)
                .withShaderDefine("NEAR_TEXEL_BITS", NearSections.BITS_PER_TEXEL)
                .withShaderDefine("NEAR_TEXEL_SHIFT", NearSections.TEXEL_SHIFT)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false);
    }

    static void bind(RenderPass pass, GeometryArena arena, ModelPublisher models, GpuTextureView lightmap,
            GpuTextureView mask, GpuBuffer frame, GpuBuffer nearSections) {
        pass.setUniform("Globals", RenderSystem.getGlobalSettingsUniform());
        pass.setUniform("FarFrame", frame);
        pass.setUniform("Quads", arena.quads());
        pass.setUniform("MeshRecords", arena.records().buffer());
        pass.setUniform("ModelRecords", models.records().buffer());
        pass.setUniform("ModelVariants", models.variants().buffer());
        pass.setUniform("NearSections", nearSections);
        pass.setUniform("Atlas", models.atlas().colourView(), models.atlas().sampler());
        pass.setUniform("TintMask", models.atlas().tintMaskView(), models.atlas().sampler());
        pass.setUniform("Lightmap", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        pass.setUniform("NearMask", mask, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    private FarQuads() {
    }
}
