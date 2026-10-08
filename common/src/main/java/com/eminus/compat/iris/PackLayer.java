package com.eminus.compat.iris;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.eminus.Eminus;
import com.eminus.client.render.far.FarDraw;
import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.render.far.FarTarget;
import com.eminus.compat.iris.mixin.ShaderPackAccessor;
import com.eminus.gpu.Foreign;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;

import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;

final class PackLayer {
    private static final NamespacedId ANY_DIMENSION = new NamespacedId("*", "*");
    private static final String NO_FOLDER = "";
    private static final int NO_TEXTURE = 0;

    private static @Nullable PackLayer current;

    private final IrisRenderingPipeline pipeline;
    private final @Nullable PackFile opaqueFile;
    private final @Nullable PackFile translucentFile;
    private final @Nullable ProgramSet programSet;
    private final @Nullable ShaderProperties properties;

    private @Nullable FarRenderer builtFor;
    private @Nullable PackProgram opaque;
    private @Nullable PackProgram translucent;
    private @Nullable GlFramebuffer opaqueFramebuffer;
    private @Nullable GlFramebuffer translucentFramebuffer;
    private int opaqueAttached = NO_TEXTURE;
    private int translucentAttached = NO_TEXTURE;
    private @Nullable FarDraw drawn;

    private record PackFile(String path, String source) {
    }

    private PackLayer(IrisRenderingPipeline pipeline, @Nullable ShaderPack pack, NamespacedId dimension) {
        this.pipeline = pipeline;
        if (pack == null) {
            opaqueFile = null;
            translucentFile = null;
            programSet = null;
            properties = null;
            return;
        }

        ShaderPackAccessor access = (ShaderPackAccessor) pack;
        String folder = folder(pack.getDimensionMap(), access, dimension);
        opaqueFile = read(access.eminus$sourceProvider(), PackContract.OPAQUE_FILE, folder);
        translucentFile = read(access.eminus$sourceProvider(), PackContract.TRANSLUCENT_FILE, folder);
        programSet = pack.getProgramSet(dimension);
        properties = access.eminus$shaderProperties();
    }

    static boolean readyFor(FarRenderer renderer) {
        boolean ready = Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline
                && of(pipeline).ready(renderer);
        if (!ready) {
            IrisFarState.clear();
        }
        return ready;
    }

    static void draw(FarRenderer renderer, Object pipeline, boolean translucent) {
        PackLayer layer = of((IrisRenderingPipeline) pipeline);
        if (translucent) {
            layer.drawTranslucent();
        } else {
            layer.drawOpaque(renderer);
        }
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
            current.drawn = null;
        }
    }

    private static PackLayer of(IrisRenderingPipeline pipeline) {
        if (current == null || current.pipeline != pipeline) {
            current = new PackLayer(pipeline, Iris.getCurrentPack().orElse(null), Iris.getCurrentDimension());
        }
        return current;
    }

    private static String folder(Map<NamespacedId, String> dimensions, ShaderPackAccessor access,
            NamespacedId dimension) {
        String own = dimensions.get(dimension);
        return own != null && access.eminus$dimensionIds().contains(own) ? own
                : dimensions.getOrDefault(ANY_DIMENSION, NO_FOLDER);
    }

    private static @Nullable PackFile read(Function<AbsolutePackPath, String> sources, String file, String folder) {
        for (String path : PackSources.candidates(file, folder)) {
            String source = sources.apply(AbsolutePackPath.fromAbsolutePath(path));
            if (source != null) {
                return new PackFile(path, source);
            }
        }
        return null;
    }

    private boolean ready(FarRenderer renderer) {
        if (opaqueFile == null) {
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
        FarDraw draw = renderer.farDraw();
        Optional<Foreign> foreign = draw.gpu().foreign();
        if (foreign.isEmpty()) {
            Eminus.LOGGER.warn("Shader pack file {} is not used: the far layer runs on the game's GPU path",
                    opaqueFile.path());
            return;
        }

        PackProgram builtOpaque = PackProgram.build(draw, foreign.get(), pipeline, programSet, properties,
                opaqueFile.path(), opaqueFile.source(), false);
        PackFile translucentSource = translucentFile == null ? opaqueFile : translucentFile;
        PackProgram builtTranslucent = builtOpaque == null ? null : PackProgram.build(draw, foreign.get(), pipeline,
                programSet, properties, translucentSource.path(), translucentSource.source(), true);
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
                draw.previousProjection(), draw.farBlocks());
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
