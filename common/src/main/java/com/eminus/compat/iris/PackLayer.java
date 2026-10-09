package com.eminus.compat.iris;

import java.util.Optional;
import java.util.function.ToIntFunction;

import com.eminus.Eminus;
import com.eminus.client.render.far.FarDraw;
import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.render.far.FarShadow;
import com.eminus.client.render.far.FarTarget;
import com.eminus.compat.iris.contract.ContractPath;
import com.eminus.compat.iris.dh.DhPath;
import com.eminus.compat.iris.mixin.ShaderPackAccessor;
import com.eminus.gpu.Foreign;
import com.eminus.settings.FarDistance;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.irisshaders.iris.shadows.ShadowRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

final class PackLayer {
    private static final int NO_TEXTURE = 0;
    private static final int NO_REACH = 0;

    private static @Nullable PackLayer current;

    private final IrisRenderingPipeline pipeline;
    private final @Nullable PackPath path;
    private final @Nullable ProgramSet programSet;
    private final @Nullable ShaderProperties properties;

    private @Nullable FarRenderer builtFor;
    private @Nullable PackProgram opaque;
    private @Nullable PackProgram translucent;
    private @Nullable PackProgram shadow;
    private @Nullable GlFramebuffer opaqueFramebuffer;
    private @Nullable GlFramebuffer translucentFramebuffer;
    private @Nullable GlFramebuffer shadowFramebuffer;
    private int opaqueAttached = NO_TEXTURE;
    private int translucentAttached = NO_TEXTURE;
    private @Nullable FarDraw drawn;

    private PackLayer(IrisRenderingPipeline pipeline, @Nullable ShaderPack pack, NamespacedId dimension) {
        this.pipeline = pipeline;
        if (pack == null) {
            path = null;
            programSet = null;
            properties = null;
            return;
        }

        programSet = pack.getProgramSet(dimension);
        PackPath contract = ContractPath.detect(pack, dimension);
        path = contract != null ? contract : DhPath.detect(pack, pipeline, programSet);
        properties = ((ShaderPackAccessor) pack).eminus$shaderProperties();
    }

    static @Nullable ToIntFunction<BlockState> packIds(@Nullable ToIntFunction<BlockState> rendered) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) {
            return rendered;
        }

        if (!(pipeline instanceof IrisRenderingPipeline iris)) {
            return null;
        }

        PackPath path = of(iris).path;
        return path == null ? null : path.blockIds(iris, rendered);
    }

    static boolean readyFor(FarRenderer renderer) {
        boolean ready = Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline
                && of(pipeline).ready(renderer);
        if (!ready) {
            IrisFarState.clear();
        }
        return ready;
    }

    static boolean throughDhPrograms() {
        return current != null && current.path != null && current.path.distantHorizons();
    }

    static void draw(FarRenderer renderer, Object pipeline, boolean translucent) {
        PackLayer layer = of((IrisRenderingPipeline) pipeline);
        if (translucent) {
            layer.drawTranslucent();
        } else {
            layer.drawOpaque(renderer);
        }
    }

    static void drawShadow(FarRenderer renderer, Matrix4fc shadowView, Matrix4fc shadowProjection) {
        if (!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline)) {
            return;
        }

        PackLayer layer = of(pipeline);
        if (!layer.ready(renderer) || layer.shadow == null
                || Boolean.getBoolean(IrisShaderPack.SHADOW_OFF_PROPERTY)) {
            return;
        }

        FarShadow drawn = renderer.shadowFrame(Minecraft.getInstance(), shadowView, shadowProjection);
        if (drawn != null) {
            layer.shadow.drawShadow(renderer.farDraw(), drawn, layer.shadowFramebuffer, ShadowRenderer.RESOLUTION);
        }
    }

    static int shadowReach(int irisChunks) {
        int ours = current == null || current.shadow == null && !IrisFarState.distantHorizons() ? NO_REACH
                : Math.ceilDiv(IrisFarState.renderDistance(), FarDistance.BLOCKS_PER_CHUNK);
        return Math.max(irisChunks, ours);
    }

    static void destroyed(Object pipeline) {
        if (current != null && current.pipeline == pipeline) {
            current = null;
            IrisFarState.clear();
        }
    }

    static void rendererStopped() {
        IrisFarState.clear();
        if (current != null) {
            current.builtFor = null;
            current.opaque = null;
            current.translucent = null;
            current.shadow = null;
            current.drawn = null;
            current.opaqueAttached = NO_TEXTURE;
            current.translucentAttached = NO_TEXTURE;
        }
    }

    private static PackLayer of(IrisRenderingPipeline pipeline) {
        if (current == null || current.pipeline != pipeline) {
            current = new PackLayer(pipeline, Iris.getCurrentPack().orElse(null), Iris.getCurrentDimension());
        }
        return current;
    }

    private boolean ready(FarRenderer renderer) {
        if (path == null) {
            return false;
        }

        if (builtFor != renderer) {
            build(renderer);
        }
        return opaque != null && translucent != null;
    }

    private void build(FarRenderer renderer) {
        builtFor = renderer;
        opaque = null;
        translucent = null;
        shadow = null;
        FarDraw draw = renderer.farDraw();
        Optional<Foreign> foreign = draw.gpu().foreign();
        if (foreign.isEmpty()) {
            Eminus.LOGGER.warn("Shader pack file {} is not used: the far layer runs on the game's GPU path",
                    path.file());
            return;
        }

        PackProgram builtOpaque = program(draw, foreign.get(), PackProgram.Kind.OPAQUE);
        PackProgram builtTranslucent = builtOpaque == null ? null
                : program(draw, foreign.get(), PackProgram.Kind.TRANSLUCENT);
        if (builtOpaque == null || builtTranslucent == null) {
            return;
        }

        if (opaqueFramebuffer == null) {
            opaqueFramebuffer = pipeline.createDHFramebuffer(builtOpaque.source(), false);
        }
        if (translucentFramebuffer == null) {
            translucentFramebuffer = pipeline.createDHFramebuffer(builtTranslucent.source(), true);
        }
        opaque = builtOpaque;
        translucent = builtTranslucent;
        buildShadow(draw, foreign.get());
    }

    private void buildShadow(FarDraw draw, Foreign foreign) {
        if (!pipeline.hasShadowRenderTargets()) {
            return;
        }

        PackProgram built = program(draw, foreign, PackProgram.Kind.SHADOW);
        if (built == null) {
            return;
        }

        if (shadowFramebuffer == null) {
            shadowFramebuffer = pipeline.createDHFramebufferShadow(built.source());
        }
        shadow = built;
    }

    private @Nullable PackProgram program(FarDraw draw, Foreign foreign, PackProgram.Kind kind) {
        PackPath.Source source = path.source(kind);
        return source == null ? null
                : PackProgram.build(draw, foreign, pipeline, programSet, properties, source, kind,
                        path.distantHorizons());
    }

    private void drawOpaque(FarRenderer renderer) {
        drawn = null;
        if (!ready(renderer)) {
            IrisFarState.clear();
            return;
        }

        FarDraw draw = renderer.packFrame(Minecraft.getInstance());
        if (draw == null) {
            IrisFarState.clear();
            return;
        }

        Foreign foreign = draw.gpu().foreign().orElseThrow();
        FarTarget target = draw.target();
        int depth = foreign.texture(target.depth());
        IrisFarState.write(depth, foreign.texture(target.opaqueDepth()), draw.projection(),
                draw.previousProjection(), draw.farBlocks(), path.distantHorizons());
        opaqueAttached = attach(opaqueFramebuffer, opaqueAttached, depth);
        opaque.draw(draw, opaqueFramebuffer, false);
        foreign.copyDepth(target.colour(), target.depth(), target.opaqueDepth());
        drawn = draw;
    }

    private void drawTranslucent() {
        FarDraw draw = drawn;
        drawn = null;
        if (draw == null || translucent == null) {
            return;
        }

        int depth = draw.gpu().foreign().orElseThrow().texture(draw.target().depth());
        translucentAttached = attach(translucentFramebuffer, translucentAttached, depth);
        translucent.draw(draw, translucentFramebuffer, true);
    }

    private static int attach(GlFramebuffer framebuffer, int attached, int depth) {
        if (attached != depth) {
            framebuffer.addDepthAttachment(depth);
        }
        return depth;
    }
}
