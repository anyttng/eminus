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
import com.eminus.model.port.VariantDraw;
import com.eminus.render.arena.ArenaAllocator;
import com.eminus.render.far.DrawCommands;
import com.eminus.client.handoff.NearSectionTable;
import com.eminus.client.model.ModelRecords;
import com.eminus.client.model.ModelVariants;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.client.render.arena.MeshRecords;
import com.eminus.gpu.Capabilities;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;

import net.minecraft.resources.Identifier;

final class FarQuads {
    static final float SHADE_BLADE = 1.0F;
    static final int MAX_SAMPLES = 8;

    private static final int MAX_GROUP_INDICES = MeshBuffer.MAX_QUADS_PER_GROUP * DrawCommands.INDICES_PER_QUAD;
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Eminus.MODID, "core/far_quads");
    private static final String FRAME = "FarFrame";
    private static final String QUADS = "Quads";
    private static final String MESH_RECORDS = "MeshRecords";
    private static final String MODEL_RECORDS = "ModelRecords";
    private static final String MODEL_VARIANTS = "ModelVariants";
    private static final String NEAR_SECTIONS = "NearSections";
    private static final String ATLAS = "Atlas";
    private static final String TINT_MASK = "TintMask";
    private static final String LIGHTMAP = "Lightmap";
    private static final String NEXT_LONG_MODULO = "VARIANT_NEXT_LONG_MODULO";

    static PipelineSpec.Builder pipeline(Identifier location, float alphaCutout, Capabilities capabilities,
            VariantDraw variantDraw) {
        PipelineSpec.Builder builder = PipelineSpec.builder(location, SHADER, SHADER)
                .withBinding(Binding.uniform(FRAME))
                .withBinding(Binding.texel(QUADS, GeometryArena.QUAD_FORMAT))
                .withBinding(Binding.texel(MESH_RECORDS, MeshRecords.TEXEL_FORMAT))
                .withBinding(Binding.texel(MODEL_RECORDS, ModelRecords.TEXEL_FORMAT))
                .withBinding(Binding.texel(MODEL_VARIANTS, ModelVariants.TEXEL_FORMAT))
                .withBinding(Binding.texel(NEAR_SECTIONS, NearSectionTable.TEXEL_FORMAT))
                .withBinding(Binding.sampled(ATLAS))
                .withBinding(Binding.sampled(TINT_MASK))
                .withBinding(Binding.sampled(LIGHTMAP))
                .withDefine("QUADS_PER_BLOCK", ArenaAllocator.QUADS_PER_BLOCK)
                .withDefine("VOXELS_PER_SIDE", DetailLevel.VOXELS_PER_SIDE)
                .withDefine("MIN_HORIZONTAL", CellKey.MIN_HORIZONTAL)
                .withDefine("MIN_VERTICAL", CellKey.MIN_VERTICAL)
                .withDefine("MODEL_FACES", BakedModel.FACE_COUNT)
                .withDefine("FIRST_BLADE_FACE", Quad.FIRST_BLADE_FACE)
                .withDefine("FLUID_FLAG", ModelMetadata.FLUID)
                .withDefine("CORNER_STEPS", FluidCorners.STEPS)
                .withDefine("FACE_SIDE", BakedModel.FACE_SIDE)
                .withDefine("MAX_VARIANT_REJECTIONS", BakedModel.MAX_VARIANT_REJECTIONS)
                .withDefine("ALPHA_CUTOUT", alphaCutout)
                .withDefine("SHADE_BLADE", SHADE_BLADE)
                .withDefine("MAX_SAMPLES", MAX_SAMPLES)
                .withDefine("NEAR_SECTION_BLOCKS", NearSections.SECTION_BLOCKS)
                .withDefine("NEAR_TEXEL_BITS", NearSections.BITS_PER_TEXEL)
                .withDefine("NEAR_TEXEL_SHIFT", NearSections.TEXEL_SHIFT);
        if (variantDraw == VariantDraw.NEXT_LONG_MODULO) {
            builder.withDefine(NEXT_LONG_MODULO);
        }

        return capabilities.lightmapHalfTexel() ? builder.withDefine("LIGHTMAP_HALF_TEXEL") : builder;
    }

    static void bind(Pass pass, GeometryArena arena, ModelPublisher models, Texture lightmap, Buffer frame,
            TexelView nearSections) {
        pass.bind(FRAME, frame);
        pass.bind(QUADS, arena.quads());
        pass.bind(MESH_RECORDS, arena.records().texels());
        pass.bind(MODEL_RECORDS, models.records().texels());
        pass.bind(MODEL_VARIANTS, models.variants().texels());
        pass.bind(NEAR_SECTIONS, nearSections);
        pass.bind(ATLAS, models.atlas().colour(), Sampler.NEAREST_MIPPED);
        pass.bind(TINT_MASK, models.atlas().tintMask(), Sampler.NEAREST_MIPPED);
        pass.bind(LIGHTMAP, lightmap, Sampler.LINEAR);
        pass.quadIndices(MAX_GROUP_INDICES);
    }

    private FarQuads() {
    }
}
