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
import com.eminus.gpu.texture.Texture;
import com.eminus.model.port.VariantDraw;
import com.eminus.render.backend.DepthConvention;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

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
    private int farBlocks;

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

    void frame(Buffer commands, int opaqueCount, int translucentCount, Texture lightmap, Matrix4fc projection,
            Matrix4fc view, int farBlocks) {
        previousProjection.set(this.projection);
        depth.forward(projection, this.projection);
        this.view.set(view);
        this.commands = commands;
        this.opaqueCount = opaqueCount;
        this.translucentCount = translucentCount;
        this.lightmap = lightmap;
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

    public PipelineSpec packPipeline(Location location, boolean translucent) {
        PipelineSpec.Builder builder = FarQuads.pipeline(location, PACK_SHADER,
                        translucent ? TranslucentPass.ALPHA_CUTOUT : OpaquePass.ALPHA_CUTOUT, gpu.capabilities(),
                        variantDraw)
                .withColourTarget(FarTarget.COLOUR_FORMAT, translucent ? Blend.TRANSLUCENT : null, true)
                .withDepthTest(depth.compare(), true);
        return (translucent ? builder.withDefine("TRANSLUCENT_PASS") : builder.withDefine("FULL_COVERAGE")).build();
    }

    public void bind(Pass pass) {
        FarQuads.bind(pass, arena, models, lightmap, frame.buffer(), nearSections.texels());
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
