package com.eminus.client.render.far;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.handoff.NearSections;
import com.eminus.mesh.Quad;
import com.eminus.model.BakedModel;
import com.eminus.render.arena.ArenaAllocator;
import com.eminus.client.render.arena.GeometryArena;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

final class FarQuads {
    // The near field bakes these into its vertex colour through BlockModelLighter.AdjacencyInfo.
    static final float SHADE_DOWN = 0.5F;
    static final float SHADE_UP = 1.0F;
    static final float SHADE_NORTH_SOUTH = 0.8F;
    static final float SHADE_WEST_EAST = 0.6F;

    static final float SHADE_BLADE = 1.0F;

    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/far_quads");

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("FarFrame", UniformType.UNIFORM_BUFFER)
            .withUniform("Quads", UniformType.TEXEL_BUFFER, GpuFormat.RG32_UINT)
            .withUniform("MeshRecords", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT)
            .withUniform("ModelRecords", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
            .withUniform("TintColours", UniformType.TEXEL_BUFFER, GpuFormat.R32_UINT)
            .withUniform("NearSections", UniformType.TEXEL_BUFFER, GpuFormat.R32_UINT)
            .withSampler("Atlas")
            .withSampler("TintMask")
            .withSampler("Lightmap")
            .withSampler("NearMask")
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
                .withShaderDefine("BIOME_STRIDE", TintTable.BIOME_STRIDE)
                .withShaderDefine("MIN_HORIZONTAL", CellKey.MIN_HORIZONTAL)
                .withShaderDefine("MIN_VERTICAL", CellKey.MIN_VERTICAL)
                .withShaderDefine("MODEL_FACES", BakedModel.FACE_COUNT)
                .withShaderDefine("FIRST_BLADE_FACE", Quad.FIRST_BLADE_FACE)
                .withShaderDefine("FACE_SIDE", BakedModel.FACE_SIDE)
                .withShaderDefine("ALPHA_CUTOUT", alphaCutout)
                .withShaderDefine("SHADE_DOWN", SHADE_DOWN)
                .withShaderDefine("SHADE_UP", SHADE_UP)
                .withShaderDefine("SHADE_NORTH_SOUTH", SHADE_NORTH_SOUTH)
                .withShaderDefine("SHADE_WEST_EAST", SHADE_WEST_EAST)
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
        pass.setUniform("TintColours", models.tints().buffer());
        pass.setUniform("NearSections", nearSections);
        pass.bindTexture("Atlas", models.atlas().colourView(), atlasSampler());
        pass.bindTexture("TintMask", models.atlas().tintMaskView(), atlasSampler());
        pass.bindTexture("Lightmap", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        pass.bindTexture("NearMask", mask, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    private static GpuSampler atlasSampler() {
        return RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.NEAREST, FilterMode.NEAREST, true);
    }

    private FarQuads() {
    }
}
