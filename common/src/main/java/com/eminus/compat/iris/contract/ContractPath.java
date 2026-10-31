package com.eminus.compat.iris.contract;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.eminus.client.render.far.FarDraw;
import com.eminus.compat.iris.PackContract;
import com.eminus.compat.iris.PackPath;
import com.eminus.compat.iris.PackProgram;
import com.eminus.compat.iris.PackSlot;
import com.eminus.compat.iris.mixin.IrisRenderingPipelineAccessor;
import com.eminus.compat.iris.mixin.ShaderPackAccessor;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.ints.IntSet;

import org.jspecify.annotations.Nullable;

public final class ContractPath implements PackPath {
    private static final NamespacedId ANY_DIMENSION = new NamespacedId("*", "*");
    private static final String NO_FOLDER = "";
    private static final String OPAQUE_PROGRAM = "eminus_opaque";
    private static final String TRANSLUCENT_PROGRAM = "eminus_translucent";
    private static final String SHADOW_PROGRAM = "eminus_shadow";
    private static final String SHADOW_TRANSLUCENT_PROGRAM = "eminus_shadow_translucent";
    private static final String BEAM_PROGRAM = "eminus_beaconbeam";
    private static final String BEAM_GLOW_PROGRAM = "eminus_beaconbeam_glow";
    private static final String PACK_PROGRAM_SUFFIX = "_eminus";
    private static final String FILE_ROOT = "/";
    private static final String CHOSEN = "shadowSource picks only a source that is there";

    private final PackFile opaque;
    private final @Nullable PackFile translucent;
    private final @Nullable PackFile shadow;
    private final @Nullable PackFile shadowTranslucent;
    private final @Nullable PackFile shadowVertex;
    private final @Nullable PackFile vertex;
    private final @Nullable PackFile beam;
    private final @Nullable ProgramSource packShadow;
    private final ContractDeclarations declarations;

    private record PackFile(String path, String source) {
    }

    private ContractPath(PackFile opaque, @Nullable PackFile translucent, @Nullable PackFile shadow,
            @Nullable PackFile shadowTranslucent, @Nullable PackFile shadowVertex, @Nullable PackFile vertex,
            @Nullable PackFile beam, @Nullable ProgramSource packShadow, ContractDeclarations declarations) {
        this.opaque = opaque;
        this.translucent = translucent;
        this.shadow = shadow;
        this.shadowTranslucent = shadowTranslucent;
        this.shadowVertex = shadowVertex;
        this.vertex = vertex;
        this.beam = beam;
        this.packShadow = packShadow;
        this.declarations = declarations;
    }

    public static @Nullable ContractPath detect(ShaderPack pack, NamespacedId dimension, ProgramSet programSet) {
        ShaderPackAccessor access = (ShaderPackAccessor) pack;
        String folder = folder(pack.getDimensionMap(), access, dimension);
        Function<AbsolutePackPath, String> sources = access.eminus$sourceProvider();
        PackFile opaque = read(sources, PackContract.OPAQUE_FILE, folder);
        return opaque == null ? null : new ContractPath(opaque,
                read(sources, PackContract.TRANSLUCENT_FILE, folder), read(sources, PackContract.SHADOW_FILE, folder),
                read(sources, PackContract.SHADOW_TRANSLUCENT_FILE, folder),
                read(sources, PackContract.SHADOW_VERTEX_FILE, folder), read(sources, PackContract.VERTEX_FILE, folder),
                read(sources, PackContract.BEAM_FILE, folder),
                new ProgramFallbackResolver(programSet).resolveNullable(ProgramId.ShadowCutout),
                ((DeclaredContract) pack).eminus$contractDeclarations());
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
    public PackSlot translucentSlot() {
        return PackSlot.BEFORE_COMPOSITES;
    }

    @Override
    public @Nullable ToIntFunction<BlockState> blockIds(IrisRenderingPipeline pipeline,
            @Nullable ToIntFunction<BlockState> rendered) {
        return ((IrisRenderingPipelineAccessor) pipeline).eminus$initializedBlockIds()
                ? WorldRenderingSettings.INSTANCE.getBlockStateIds() : rendered;
    }

    @Override
    public IntSet movedIds() {
        return declarations.moved();
    }

    @Override
    public @Nullable Source source(PackProgram.Kind kind) {
        FarDraw.PackVertex stage = vertex == null ? FarDraw.PackVertex.OURS : FarDraw.PackVertex.HOOK;
        return switch (kind) {
            case OPAQUE -> source(OPAQUE_PROGRAM, opaque, vertex, stage);
            case TRANSLUCENT -> source(TRANSLUCENT_PROGRAM, translucent == null ? opaque : translucent, vertex, stage);
            case SHADOW -> switch (ContractSources.shadowSource(shadow != null, packShadow != null)) {
                case FILE -> shadowSource(SHADOW_PROGRAM, Objects.requireNonNull(shadow, CHOSEN));
                case PACK_PROGRAM -> packShadowSource(Objects.requireNonNull(packShadow, CHOSEN));
                case NONE -> null;
            };
            case SHADOW_TRANSLUCENT -> shadowTranslucent == null ? null
                    : shadowSource(SHADOW_TRANSLUCENT_PROGRAM, shadowTranslucent);
            case BEAM -> beam == null ? null
                    : source(BEAM_PROGRAM, beam, vertex, stage, ContractSources::spliceBeam);
            case BEAM_GLOW -> beam == null ? null
                    : source(BEAM_GLOW_PROGRAM, beam, vertex, stage, ContractSources::spliceBeam);
        };
    }

    private Source shadowSource(String programName, PackFile file) {
        FarDraw.PackVertex stage = ContractSources.shadowStage(vertex != null, shadowVertex != null);
        return source(programName, file, stage == FarDraw.PackVertex.HOOK ? vertex : shadowVertex, stage);
    }

    // Iris remaps depth for the shadow pass only under a name it reads as one: shadow, shadow_*, *_shadow.
    private static Source packShadowSource(ProgramSource program) {
        return new Source(program.getName() + PACK_PROGRAM_SUFFIX, FILE_ROOT + program.getName(),
                ours -> ContractSources.spliceShadowFragment(stage(program, program.getFragmentSource()),
                        PackPath.header(ours)),
                ours -> ContractSources.spliceShadowVertex(stage(program, program.getVertexSource()),
                        PackPath.header(ours)),
                FarDraw.PackVertex.SHADOW_PROGRAM, Cutout.PACK);
    }

    private static String stage(ProgramSource program, Optional<String> stage) {
        if (program.getGeometrySource().isPresent() || program.getTessControlSource().isPresent()
                || program.getTessEvalSource().isPresent()) {
            throw new IllegalArgumentException("The shadow program " + program.getName()
                    + " carries a geometry or tessellation stage");
        }
        return stage.orElseThrow();
    }

    private Source source(String programName, PackFile file, @Nullable PackFile vertexFile,
            FarDraw.PackVertex stage) {
        return source(programName, file, vertexFile, stage, ContractSources::splice);
    }

    private Source source(String programName, PackFile file, @Nullable PackFile vertexFile,
            FarDraw.PackVertex stage, BinaryOperator<String> splice) {
        return new Source(programName, file.path(),
                ours -> splice.apply(file.source(), PackPath.header(ours)),
                vertexFile == null ? null
                        : ours -> ContractSources.spliceVertex(vertexFile.source(), PackPath.header(ours)),
                stage, declarations.packCuts() ? Cutout.PACK : Cutout.IRIS);
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
