package com.eminus.compat.iris;

import java.util.Optional;

import com.eminus.client.render.far.AlphaCut;
import com.eminus.client.render.far.OpaquePass;
import com.eminus.client.render.far.TranslucentPass;

import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import net.irisshaders.iris.gl.blending.AlphaTests;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import org.jspecify.annotations.Nullable;

final class PackAlphaTest {
    static @Nullable AlphaCut cut(ProgramSet programSet, PackProgram.Kind kind, PackPath.Cutout cutout) {
        AlphaCut ours = AlphaCut.atLeast(kind == PackProgram.Kind.TRANSLUCENT
                || kind == PackProgram.Kind.SHADOW_TRANSLUCENT ? TranslucentPass.ALPHA_CUTOUT
                        : OpaquePass.ALPHA_CUTOUT);
        if (cutout == PackPath.Cutout.OURS) {
            return ours;
        }

        GameProgram game = gameProgram(kind);
        Optional<ProgramSource> program = new ProgramFallbackResolver(programSet).resolve(game.id());
        boolean irisTests = program.flatMap(ProgramSource::getFragmentSource).map(IrisAlphaTestRule::applies)
                .orElse(true);
        AlphaTest test = irisTests ? program.flatMap(source -> source.getDirectives().getAlphaTestOverride())
                .orElse(game.alphaTest()) : AlphaTest.ALWAYS;
        if (test.function() == AlphaTestFunction.ALWAYS) {
            return cutout == PackPath.Cutout.PACK ? null : ours;
        }
        return new AlphaCut(compare(test.function()), test.reference());
    }

    private record GameProgram(ProgramId id, AlphaTest alphaTest) {
    }

    private static GameProgram gameProgram(PackProgram.Kind kind) {
        return switch (kind) {
            case OPAQUE -> new GameProgram(ProgramId.TerrainCutout, AlphaTests.HALF_ALPHA);
            case TRANSLUCENT -> new GameProgram(ProgramId.Water, AlphaTests.NON_ZERO_ALPHA);
            case SHADOW -> new GameProgram(ProgramId.ShadowCutout, AlphaTests.HALF_ALPHA);
            case SHADOW_TRANSLUCENT -> new GameProgram(ProgramId.ShadowWater, AlphaTest.ALWAYS);
            case BEAM, BEAM_GLOW -> throw new IllegalArgumentException("A beam program cuts in its own pass");
        };
    }

    private static AlphaCut.Compare compare(AlphaTestFunction function) {
        return switch (function) {
            case NEVER -> AlphaCut.Compare.NEVER;
            case LESS -> AlphaCut.Compare.LESS;
            case EQUAL -> AlphaCut.Compare.EQUAL;
            case LEQUAL -> AlphaCut.Compare.LEQUAL;
            case GREATER -> AlphaCut.Compare.GREATER;
            case NOTEQUAL -> AlphaCut.Compare.NOTEQUAL;
            case GEQUAL -> AlphaCut.Compare.GEQUAL;
            case ALWAYS -> throw new IllegalArgumentException("ALWAYS cuts nothing");
        };
    }

    private PackAlphaTest() {
    }
}
