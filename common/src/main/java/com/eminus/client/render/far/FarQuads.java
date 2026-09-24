package com.eminus.client.render.far;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.handoff.NearSections;
import com.eminus.mesh.FluidCorners;
import com.eminus.mesh.MeshBuffer;
import com.eminus.mesh.Quad;
import com.eminus.model.BakedModel;
import com.eminus.model.ModelMetadata;
import com.eminus.render.arena.ArenaAllocator;
import com.eminus.render.far.DrawCommands;
import com.eminus.client.gpu.game.GameTypes;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.gpu.Format;

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
    static final float SHADE_BLADE = 1.0F;
    static final int MAX_SAMPLES = 8;

    private static final int MAX_GROUP_INDICES = MeshBuffer.MAX_QUADS_PER_GROUP * DrawCommands.INDICES_PER_QUAD;
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/far_quads");

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("FarFrame", UniformType.UNIFORM_BUFFER)
            .withUniform("Quads", UniformType.TEXEL_BUFFER, GameTypes.format(Format.RG32_UINT))
            .withUniform("MeshRecords", UniformType.TEXEL_BUFFER, GameTypes.format(Format.RGBA32_UINT))
            .withUniform("ModelRecords", UniformType.TEXEL_BUFFER, GameTypes.format(Format.RGBA32_FLOAT))
            .withUniform("ModelVariants", UniformType.TEXEL_BUFFER, GameTypes.format(Format.RG32_UINT))
            .withUniform("NearSections", UniformType.TEXEL_BUFFER, GameTypes.format(Format.R32_UINT))
            .withSampler("Atlas")
            .withSampler("TintMask")
            .withSampler("Lightmap")
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
                .withShaderDefine("FLUID_FLAG", ModelMetadata.FLUID)
                .withShaderDefine("CORNER_STEPS", FluidCorners.STEPS)
                .withShaderDefine("FACE_SIDE", BakedModel.FACE_SIDE)
                .withShaderDefine("MAX_VARIANT_REJECTIONS", BakedModel.MAX_VARIANT_REJECTIONS)
                .withShaderDefine("ALPHA_CUTOUT", alphaCutout)
                .withShaderDefine("SHADE_BLADE", SHADE_BLADE)
                .withShaderDefine("MAX_SAMPLES", MAX_SAMPLES)
                .withShaderDefine("NEAR_SECTION_BLOCKS", NearSections.SECTION_BLOCKS)
                .withShaderDefine("NEAR_TEXEL_BITS", NearSections.BITS_PER_TEXEL)
                .withShaderDefine("NEAR_TEXEL_SHIFT", NearSections.TEXEL_SHIFT)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false);
    }

    static void bind(RenderPass pass, GeometryArena arena, ModelPublisher models, GpuTextureView lightmap,
            GpuBuffer frame, GpuBuffer nearSections) {
        pass.setUniform("Globals", RenderSystem.getGlobalSettingsUniform());
        pass.setUniform("FarFrame", frame);
        pass.setUniform("Quads", GameTypes.buffer(arena.quads()));
        pass.setUniform("MeshRecords", GameTypes.buffer(arena.records().buffer()));
        pass.setUniform("ModelRecords", models.records().buffer());
        pass.setUniform("ModelVariants", models.variants().buffer());
        pass.setUniform("NearSections", nearSections);
        pass.bindTexture("Atlas", GameTypes.view(models.atlas().colour()), atlasSampler());
        pass.bindTexture("TintMask", GameTypes.view(models.atlas().tintMask()), atlasSampler());
        pass.bindTexture("Lightmap", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(MAX_GROUP_INDICES);
        pass.setIndexBuffer(indexBuffer, indices.type());
    }

    static void reserveIndices() {
        RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(MAX_GROUP_INDICES);
    }

    private static GpuSampler atlasSampler() {
        return RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.NEAREST, FilterMode.NEAREST, true);
    }

    private FarQuads() {
    }
}
