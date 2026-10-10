package com.eminus.compat.iris.dh;

import java.util.Optional;
import java.util.function.ToIntFunction;

import com.eminus.client.render.far.FarDraw;
import com.eminus.compat.iris.PackPath;
import com.eminus.compat.iris.PackProgram;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

public final class DhPath implements PackPath {
    private static final String PROGRAM_PREFIX = "eminus_";
    private static final String FILE_ROOT = "/";
    private static final boolean SHADOW_UNLESS_DISABLED = true;

    private final ProgramSource terrain;
    private final ProgramSource water;
    private final @Nullable ProgramSource shadow;

    private DhPath(ProgramSource terrain, ProgramSource water, @Nullable ProgramSource shadow) {
        this.terrain = terrain;
        this.water = water;
        this.shadow = shadow;
    }

    public static @Nullable DhPath detect(ShaderPack pack, IrisRenderingPipeline pipeline, ProgramSet programSet) {
        if (!((DistantHorizonsPack) pack).eminus$distantHorizons()) {
            return null;
        }

        Optional<ProgramSource> terrain = pipeline.getDHTerrainShader().flatMap(ProgramSource::requireValid);
        return terrain.map(found -> new DhPath(found,
                pipeline.getDHWaterShader().flatMap(ProgramSource::requireValid).orElse(found),
                shadow(pipeline, programSet))).orElse(null);
    }

    private static @Nullable ProgramSource shadow(IrisRenderingPipeline pipeline, ProgramSet programSet) {
        if (!programSet.getPackDirectives().getShadowDirectives().isDhShadowEnabled()
                .orElse(SHADOW_UNLESS_DISABLED)) {
            return null;
        }
        return pipeline.getDHShadowShader().flatMap(ProgramSource::requireValid).orElse(null);
    }

    @Override
    public String file() {
        return FILE_ROOT + terrain.getName();
    }

    @Override
    public boolean distantHorizons() {
        return true;
    }

    @Override
    public ToIntFunction<BlockState> blockIds(IrisRenderingPipeline pipeline,
            @Nullable ToIntFunction<BlockState> rendered) {
        return DhMaterials.CLASSES;
    }

    @Override
    public @Nullable Source source(PackProgram.Kind kind) {
        return switch (kind) {
            case OPAQUE -> source(terrain);
            case TRANSLUCENT -> source(water);
            case SHADOW -> shadow == null ? null : source(shadow);
            case SHADOW_TRANSLUCENT -> null;
        };
    }

    private static Source source(ProgramSource program) {
        String vertex = program.getVertexSource().orElseThrow();
        String fragment = program.getFragmentSource().orElseThrow();
        return new Source(PROGRAM_PREFIX + program.getName(), FILE_ROOT + program.getName(),
                ours -> DhSources.spliceFragmentProgram(fragment, PackPath.header(ours)),
                ours -> DhSources.spliceVertexProgram(vertex, PackPath.header(ours)), FarDraw.PackVertex.PROGRAM);
    }
}
