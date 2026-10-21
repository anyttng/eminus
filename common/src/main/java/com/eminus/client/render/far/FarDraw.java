package com.eminus.client.render.far;

import com.eminus.Eminus;
import com.eminus.client.handoff.NearSectionTable;
import com.eminus.client.model.ModelPublisher;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.Location;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pipeline.Blend;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;
import com.eminus.model.port.VariantDraw;
import com.eminus.render.backend.DepthConvention;

import com.uber.nullaway.annotations.Initializer;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class FarDraw {
    private static final Location PACK_SHADER = new Location(Eminus.MODID, "core/far_pack");

    private final Gpu gpu;
    private final DepthConvention depth;
    private final VariantDraw variantDraw;
    private final GeometryArena arena;
    private final ModelPublisher models;
    private final FarFrame frame;
    private final NearSectionTable nearSections;
    private final FarTarget target;
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f previousProjection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();

    private Buffer commands;
    private int opaqueCount;
    private int translucentCount;
    private Texture lightmap;
    private Sampler atlas;
    private int farBlocks;

    public enum PackVertex {
        OURS,
        HOOK,
        SHADOW_HOOK,
        PROGRAM,
        SHADOW_PROGRAM;

        public boolean hook() {
            return this == HOOK || this == SHADOW_HOOK;
        }
    }

    FarDraw(Gpu gpu, DepthConvention depth, VariantDraw variantDraw, GeometryArena arena, ModelPublisher models,
            FarFrame frame, NearSectionTable nearSections, FarTarget target) {
        this.gpu = gpu;
        this.depth = depth;
        this.variantDraw = variantDraw;
        this.arena = arena;
        this.models = models;
        this.frame = frame;
        this.nearSections = nearSections;
        this.target = target;
    }

    @Initializer
    void frame(Buffer commands, int opaqueCount, int translucentCount, Texture lightmap, Sampler atlas,
            Matrix4fc projection, Matrix4fc view, int farBlocks) {
        previousProjection.set(this.projection);
        depth.forward(projection, this.projection);
        this.view.set(view);
        this.commands = commands;
        this.opaqueCount = opaqueCount;
        this.translucentCount = translucentCount;
        this.lightmap = lightmap;
        this.atlas = atlas;
        this.farBlocks = farBlocks;
    }

    public Gpu gpu() {
        return gpu;
    }

    public FarTarget target() {
        return target;
    }

    public Texture lightmap() {
        return lightmap;
    }

    public Matrix4fc projection() {
        return projection;
    }

    public Matrix4fc previousProjection() {
        return previousProjection;
    }

    public Matrix4fc view() {
        return view;
    }

    public int farBlocks() {
        return farBlocks;
    }

    public PipelineSpec packPipeline(Location location, boolean translucent, PackVertex vertex, boolean animated,
            boolean separateAo) {
        PipelineSpec.Builder builder = translucent
                ? pack(location, TranslucentPass.ALPHA_CUTOUT, Blend.TRANSLUCENT, true, false, vertex, animated)
                        .withDefine("TRANSLUCENT_PASS")
                : pack(location, OpaquePass.ALPHA_CUTOUT, null, true, true, vertex, animated)
                        .withDefine("FULL_COVERAGE");
        return separateAo ? builder.withDefine("SEPARATE_AO").build() : builder.build();
    }

    public PipelineSpec packDepthPipeline(Location location, PackVertex vertex, boolean animated) {
        return pack(location, TranslucentPass.ALPHA_CUTOUT, null, false, true, vertex, animated)
                .withDefine("TRANSLUCENT_PASS").build();
    }

    private PipelineSpec.Builder pack(Location location, float alphaCutout, @Nullable Blend blend,
            boolean writesColor, boolean writesDepth, PackVertex vertex, boolean animated) {
        return withVertex(FarQuads.withNearestAtlas(
                        FarQuads.pipeline(location, PACK_SHADER, alphaCutout, gpu.capabilities(), variantDraw))
                .withColorTarget(FarTarget.COLOR_FORMAT, blend, writesColor)
                .withDepthTest(depth.compare(), writesDepth), vertex, animated);
    }

    // Under a pack Iris clears the shadow depth to 1.0 and draws it forward, whatever the game's own direction.
    public PipelineSpec shadowPipeline(Location location, boolean translucent, PackVertex vertex, boolean animated) {
        PipelineSpec.Builder builder = withVertex(FarQuads.withNearestAtlas(FarQuads.pipeline(location,
                        PACK_SHADER, translucent ? TranslucentPass.ALPHA_CUTOUT : OpaquePass.ALPHA_CUTOUT,
                        gpu.capabilities(), variantDraw))
                .withColorTarget(FarTarget.COLOR_FORMAT, null, true)
                .withDepthTest(DepthConvention.of(depth.zeroToOne(), false).compare(), true)
                .withDefine(translucent ? "TRANSLUCENT_PASS" : "FULL_COVERAGE")
                .withDefine("SHADOW_PASS"), vertex, animated);
        if (vertex.hook() && depth.zeroToOne()) {
            builder.withDefine("SHADOW_ZERO_TO_ONE");
        }
        return builder.build();
    }

    private static PipelineSpec.Builder withVertex(PipelineSpec.Builder builder, PackVertex vertex, boolean animated) {
        return switch (vertex) {
            case OURS -> builder;
            case HOOK -> animated ? builder.withDefine("PACK_VERTEX")
                    : builder.withDefine("PACK_VERTEX").withDefine("STILL");
            case SHADOW_HOOK -> builder.withDefine("PACK_SHADOW_VERTEX");
            case PROGRAM -> program(builder);
            case SHADOW_PROGRAM -> program(builder).withDefine("PACK_SHADOW_PROGRAM");
        };
    }

    private static PipelineSpec.Builder program(PipelineSpec.Builder builder) {
        return builder.withDefine("DH_PROGRAM");
    }

    public void bind(Pass pass) {
        FarQuads.bind(pass, arena, models, lightmap, atlas, frame.buffer(), nearSections.texels());
        FarQuads.bindNearestAtlas(pass, models);
    }

    public void bindShadow(Pass pass, FarShadow shadow) {
        FarQuads.bind(pass, arena, models, lightmap, atlas, shadow.frameBuffer(), nearSections.texels());
        FarQuads.bindNearestAtlas(pass, models);
    }

    public void drawOpaque(Pass pass) {
        if (opaqueCount > 0) {
            pass.drawIndexedIndirect(commands, 0, opaqueCount);
        }
    }

    public void drawTranslucent(Pass pass) {
        if (translucentCount > 0) {
            pass.drawIndexedIndirect(commands, opaqueCount, translucentCount);
        }
    }
}
