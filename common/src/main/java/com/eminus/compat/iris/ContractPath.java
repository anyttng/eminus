package com.eminus.compat.iris;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.eminus.compat.iris.mixin.IrisRenderingPipelineAccessor;
import com.eminus.compat.iris.mixin.ShaderPackAccessor;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;

import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

final class ContractPath implements PackPath {
    private static final NamespacedId ANY_DIMENSION = new NamespacedId("*", "*");
    private static final String NO_FOLDER = "";
    private static final String OPAQUE_PROGRAM = "eminus_opaque";
    private static final String TRANSLUCENT_PROGRAM = "eminus_translucent";
    private static final String SHADOW_PROGRAM = "eminus_shadow";

    private final PackFile opaque;
    private final @Nullable PackFile translucent;
    private final @Nullable PackFile shadow;
    private final @Nullable PackFile shadowVertex;

    private record PackFile(String path, String source) {
    }

    private ContractPath(PackFile opaque, @Nullable PackFile translucent, @Nullable PackFile shadow,
            @Nullable PackFile shadowVertex) {
        this.opaque = opaque;
        this.translucent = translucent;
        this.shadow = shadow;
        this.shadowVertex = shadowVertex;
    }

    static @Nullable ContractPath detect(ShaderPack pack, NamespacedId dimension) {
        ShaderPackAccessor access = (ShaderPackAccessor) pack;
        String folder = folder(pack.getDimensionMap(), access, dimension);
        Function<AbsolutePackPath, String> sources = access.eminus$sourceProvider();
        PackFile opaque = read(sources, PackContract.OPAQUE_FILE, folder);
        return opaque == null ? null : new ContractPath(opaque,
                read(sources, PackContract.TRANSLUCENT_FILE, folder), read(sources, PackContract.SHADOW_FILE, folder),
                read(sources, PackContract.SHADOW_VERTEX_FILE, folder));
    }

    @Override
    public String file() {
        return opaque.path();
    }

    @Override
    public @Nullable ToIntFunction<BlockState> blockIds(IrisRenderingPipeline pipeline,
            @Nullable ToIntFunction<BlockState> rendered) {
        return ((IrisRenderingPipelineAccessor) pipeline).eminus$initializedBlockIds()
                ? WorldRenderingSettings.INSTANCE.getBlockStateIds() : rendered;
    }

    @Override
    public @Nullable Source source(PackProgram.Kind kind) {
        return switch (kind) {
            case OPAQUE -> fragmentOnly(OPAQUE_PROGRAM, opaque);
            case TRANSLUCENT -> fragmentOnly(TRANSLUCENT_PROGRAM, translucent == null ? opaque : translucent);
            case SHADOW -> shadow == null ? null : new Source(SHADOW_PROGRAM, shadow.path(),
                    ours -> PackSources.splice(shadow.source(), header(ours)),
                    shadowVertex == null ? null
                            : ours -> PackSources.spliceVertex(shadowVertex.source(), header(ours)));
        };
    }

    private static Source fragmentOnly(String programName, PackFile file) {
        return new Source(programName, file.path(), ours -> PackSources.splice(file.source(), header(ours)), null);
    }

    private static String header(String ours) {
        return PackSources.header(JcppProcessor.glslPreprocessSource(ours, List.of()));
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
}
