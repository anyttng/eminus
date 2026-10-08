package com.eminus.compat.iris;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.eminus.Eminus;
import com.eminus.client.render.far.FarDraw;
import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.render.far.FarShadow;
import com.eminus.client.render.far.FarTarget;
import com.eminus.compat.iris.mixin.IrisRenderingPipelineAccessor;
import com.eminus.compat.iris.mixin.ShaderPackAccessor;
import com.eminus.gpu.Foreign;
import com.eminus.settings.FarDistance;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.helpers.MatrixUtils;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.irisshaders.iris.shadows.ShadowRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

final class PackLayer {
    private static final NamespacedId ANY_DIMENSION = new NamespacedId("*", "*");
    private static final String NO_FOLDER = "";
    private static final int NO_TEXTURE = 0;
    private static final int NO_REACH = 0;

    private static @Nullable PackLayer current;

    private final IrisRenderingPipeline pipeline;
    private final @Nullable PackFile opaqueFile;
    private final @Nullable PackFile translucentFile;
    private final @Nullable PackFile shadowFile;
    private final @Nullable PackFile shadowVertexFile;
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

    private record PackFile(String path, String source) {
    }

    private PackLayer(IrisRenderingPipeline pipeline, @Nullable ShaderPack pack, NamespacedId dimension) {
        this.pipeline = pipeline;
        if (pack == null) {
            opaqueFile = null;
            translucentFile = null;
            shadowFile = null;
            shadowVertexFile = null;
            programSet = null;
            properties = null;
            return;
        }

        ShaderPackAccessor access = (ShaderPackAccessor) pack;
        String folder = folder(pack.getDimensionMap(), access, dimension);
        opaqueFile = read(access.eminus$sourceProvider(), PackContract.OPAQUE_FILE, folder);
        translucentFile = read(access.eminus$sourceProvider(), PackContract.TRANSLUCENT_FILE, folder);
        shadowFile = read(access.eminus$sourceProvider(), PackContract.SHADOW_FILE, folder);
        shadowVertexFile = read(access.eminus$sourceProvider(), PackContract.SHADOW_VERTEX_FILE, folder);
        programSet = pack.getProgramSet(dimension);
        properties = access.eminus$shaderProperties();
    }

    static @Nullable ToIntFunction<BlockState> packIds(@Nullable ToIntFunction<BlockState> rendered) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) {
            return rendered;
        }

        if (!(pipeline instanceof IrisRenderingPipeline iris) || of(iris).opaqueFile == null) {
            return null;
        }

        return ((IrisRenderingPipelineAccessor) iris).eminus$initializedBlockIds()
                ? WorldRenderingSettings.INSTANCE.getBlockStateIds() : rendered;
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

    static void drawShadow(FarRenderer renderer, Matrix4fc shadowView, Matrix4fc shadowProjection) {
        if (!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline)) {
            return;
        }

        PackLayer layer = of(pipeline);
        if (!layer.ready(renderer) || layer.shadow == null
                || Boolean.getBoolean(IrisShaderPack.SHADOW_OFF_PROPERTY)) {
            return;
        }

        // Iris hands the callback the device's depth range and the pack's uniforms the -1..1 one.
        FarShadow drawn = renderer.shadowFrame(Minecraft.getInstance(), shadowView,
                MatrixUtils.toMinusOneToOne(new Matrix4f(shadowProjection)));
        if (drawn != null) {
            layer.shadow.drawShadow(renderer.farDraw(), drawn, layer.shadowFramebuffer, ShadowRenderer.RESOLUTION);
        }
    }

    static int shadowReach(int irisChunks) {
        int ours = current == null || current.shadow == null ? NO_REACH
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
        shadow = null;
        FarDraw draw = renderer.farDraw();
        Optional<Foreign> foreign = draw.gpu().foreign();
        if (foreign.isEmpty()) {
            Eminus.LOGGER.warn("Shader pack file {} is not used: the far layer runs on the game's GPU path",
                    opaqueFile.path());
            return;
        }

        PackProgram builtOpaque = PackProgram.build(draw, foreign.get(), pipeline, programSet, properties,
                opaqueFile.path(), opaqueFile.source(), null, PackProgram.Kind.OPAQUE);
        PackFile translucentSource = translucentFile == null ? opaqueFile : translucentFile;
        PackProgram builtTranslucent = builtOpaque == null ? null : PackProgram.build(draw, foreign.get(), pipeline,
                programSet, properties, translucentSource.path(), translucentSource.source(), null,
                PackProgram.Kind.TRANSLUCENT);
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
        if (shadowFile == null || !pipeline.hasShadowRenderTargets()) {
            return;
        }

        PackProgram built = PackProgram.build(draw, foreign, pipeline, programSet, properties, shadowFile.path(),
                shadowFile.source(), shadowVertexFile == null ? null : shadowVertexFile.source(),
                PackProgram.Kind.SHADOW);
        if (built == null) {
            return;
        }

        if (shadowFramebuffer == null) {
            shadowFramebuffer = pipeline.createDHFramebufferShadow(built.source());
        }
        shadow = built;
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
            framebuffer.addDepthAttachmentBypass(depth);
        }
        return depth;
    }
}
