package com.eminus.render.far.client;

import java.util.Optional;
import java.util.OptionalDouble;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.model.BakedModel;
import com.eminus.render.arena.ArenaAllocator;
import com.eminus.render.arena.client.GeometryArena;
import com.eminus.render.backend.DepthConvention;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class OpaquePass {
    public static final float ALPHA_CUTOUT = 0.5F;

    // The near field bakes these into its vertex colour through BlockModelLighter.AdjacencyInfo.
    public static final float SHADE_DOWN = 0.5F;
    public static final float SHADE_UP = 1.0F;
    public static final float SHADE_NORTH_SOUTH = 0.8F;
    public static final float SHADE_WEST_EAST = 0.6F;

    private static final Identifier PIPELINE = Identifier.fromNamespaceAndPath(Eminus.MODID, "far_opaque");
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/far_opaque");
    private static final String PASS_LABEL = "eminus-far-opaque";
    private static final Vector4fc CLEAR_COLOUR = new Vector4f();

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("FarFrame", UniformType.UNIFORM_BUFFER)
            .withUniform("Quads", UniformType.TEXEL_BUFFER, GpuFormat.RG32_UINT)
            .withUniform("MeshRecords", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT)
            .withUniform("ModelRecords", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
            .withUniform("TintColours", UniformType.TEXEL_BUFFER, GpuFormat.R32_UINT)
            .withSampler("Atlas")
            .withSampler("Lightmap")
            .withSampler("Coverage")
            .build();

    private final RenderPipeline pipeline;

    private OpaquePass(RenderPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public static OpaquePass create(DepthConvention depth) {
        RenderSystem.assertOnRenderThread();
        return new OpaquePass(pipeline(depth));
    }

    public void draw(FarTarget target, GeometryArena arena, ModelPublisher models, GpuTextureView lightmap,
            GpuBufferSlice commands, int drawCount, GpuBuffer frame) {
        RenderSystem.assertOnRenderThread();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor(target))) {
            pass.setPipeline(pipeline);
            pass.setUniform("Globals", RenderSystem.getGlobalSettingsUniform());
            pass.setUniform("FarFrame", frame);
            pass.setUniform("Quads", arena.quads());
            pass.setUniform("MeshRecords", arena.records().buffer());
            pass.setUniform("ModelRecords", models.records().buffer());
            pass.setUniform("TintColours", models.tints().buffer());
            pass.bindTexture("Atlas", models.atlasView(), atlasSampler());
            pass.bindTexture("Lightmap", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.bindTexture("Coverage", target.coverageView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));

            if (drawCount > 0) {
                pass.drawIndirect(commands, drawCount);
            }
        }
    }

    private static GpuSampler atlasSampler() {
        return RenderSystem.getSamplerCache()
                .getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, true);
    }

    private static RenderPassDescriptor descriptor(FarTarget target) {
        return RenderPassDescriptor.create(() -> PASS_LABEL)
                .withColorAttachment(target.colourView(), Optional.of(CLEAR_COLOUR))
                .withDepthAttachment(target.depthStencilView(), OptionalDouble.of(DepthConvention.REVERSED_FARTHEST))
                .withRenderArea(new RenderPass.RenderArea(0, 0, target.width(), target.height()));
    }

    private static RenderPipeline pipeline(DepthConvention depth) {
        return RenderPipeline.builder()
                .withLocation(PIPELINE)
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
                .withShaderDefine("FACE_SIDE", BakedModel.FACE_SIDE)
                .withShaderDefine("ALPHA_CUTOUT", ALPHA_CUTOUT)
                .withShaderDefine("SHADE_DOWN", SHADE_DOWN)
                .withShaderDefine("SHADE_UP", SHADE_UP)
                .withShaderDefine("SHADE_NORTH_SOUTH", SHADE_NORTH_SOUTH)
                .withShaderDefine("SHADE_WEST_EAST", SHADE_WEST_EAST)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withDepthStencilState(new DepthStencilState(depth.compare(), true))
                .withCull(false)
                .build();
    }
}
