package com.eminus.compat.iris.contract;

import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.eminus.client.render.far.FarDraw;
import com.eminus.compat.iris.PackContract;
import com.eminus.compat.iris.PackPath;
import com.eminus.compat.iris.PackProgram;
import com.eminus.compat.iris.mixin.IrisRenderingPipelineAccessor;
import com.eminus.compat.iris.mixin.ShaderPackAccessor;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;

import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

public final class ContractPath implements PackPath {
    private static final NamespacedId ANY_DIMENSION = new NamespacedId("*", "*");
    private static final String NO_FOLDER = "";
    private static final String OPAQUE_PROGRAM = "eminus_opaque";
    private static final String TRANSLUCENT_PROGRAM = "eminus_translucent";
    private static final String SHADOW_PROGRAM = "eminus_shadow";

    private final PackFile opaque;
    private final @Nullable PackFile translucent;
    private final @Nullable PackFile shadow;
    private final @Nullable PackFile shadowVertex;
    private final @Nullable PackFile vertex;

    private record PackFile(String path, String source) {
    }

    private ContractPath(PackFile opaque, @Nullable PackFile translucent, @Nullable PackFile shadow,
            @Nullable PackFile shadowVertex, @Nullable PackFile vertex) {
        this.opaque = opaque;
        this.translucent = translucent;
        this.shadow = shadow;
        this.shadowVertex = shadowVertex;
        this.vertex = vertex;
    }

    public static @Nullable ContractPath detect(ShaderPack pack, NamespacedId dimension) {
        ShaderPackAccessor access = (ShaderPackAccessor) pack;
        String folder = folder(pack.getDimensionMap(), access, dimension);
        Function<AbsolutePackPath, String> sources = access.eminus$sourceProvider();
        PackFile opaque = read(sources, PackContract.OPAQUE_FILE, folder);
        return opaque == null ? null : new ContractPath(opaque,
                read(sources, PackContract.TRANSLUCENT_FILE, folder), read(sources, PackContract.SHADOW_FILE, folder),
                read(sources, PackContract.SHADOW_VERTEX_FILE, folder), read(sources, PackContract.VERTEX_FILE, folder));
    }

    @Override
    public String file() {
        return opaque.path();
    }

    @Override
    public boolean distantHorizons() {
        return false;
    }

    @Override
    public @Nullable ToIntFunction<BlockState> blockIds(IrisRenderingPipeline pipeline,
            @Nullable ToIntFunction<BlockState> rendered) {
        return ((IrisRenderingPipelineAccessor) pipeline).eminus$initializedBlockIds()
                ? WorldRenderingSettings.INSTANCE.getBlockStateIds() : rendered;
    }

    @Override
    public @Nullable Source source(PackProgram.Kind kind) {
        FarDraw.PackVertex stage = vertex == null ? FarDraw.PackVertex.OURS : FarDraw.PackVertex.HOOK;
        return switch (kind) {
            case OPAQUE -> source(OPAQUE_PROGRAM, opaque, vertex, stage);
            case TRANSLUCENT -> source(TRANSLUCENT_PROGRAM, translucent == null ? opaque : translucent, vertex, stage);
            case SHADOW -> shadow == null ? null : shadowSource(shadow);
        };
    }

    private Source shadowSource(PackFile file) {
        FarDraw.PackVertex stage = ContractSources.shadowStage(vertex != null, shadowVertex != null);
        return source(SHADOW_PROGRAM, file, stage == FarDraw.PackVertex.HOOK ? vertex : shadowVertex, stage);
    }

    private static Source source(String programName, PackFile file, @Nullable PackFile vertexFile,
            FarDraw.PackVertex stage) {
        return new Source(programName, file.path(),
                ours -> ContractSources.splice(file.source(), PackPath.header(ours)),
                vertexFile == null ? null
                        : ours -> ContractSources.spliceVertex(vertexFile.source(), PackPath.header(ours)),
                stage);
    }

    private static String folder(Map<NamespacedId, String> dimensions, ShaderPackAccessor access,
            NamespacedId dimension) {
        String own = dimensions.get(dimension);
        return own != null && access.eminus$dimensionIds().contains(own) ? own
                : dimensions.getOrDefault(ANY_DIMENSION, NO_FOLDER);
    }

    private static @Nullable PackFile read(Function<AbsolutePackPath, String> sources, String file, String folder) {
        for (String path : ContractSources.candidates(file, folder)) {
            String source = sources.apply(AbsolutePackPath.fromAbsolutePath(path));
            if (source != null) {
                return new PackFile(path, source);
            }
        }
        return null;
    }
}
