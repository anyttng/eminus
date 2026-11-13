package com.eminus.compat.iris;

import java.util.Optional;

import com.eminus.client.render.far.AlphaCut;
import com.eminus.client.render.far.AlphaCuts;

import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import org.jspecify.annotations.Nullable;

final class PackAlphaTest {
    static @Nullable AlphaCut cut(ProgramSet programSet, PackProgram.Kind kind, PackPath.Cutout cutout) {
        AlphaCut ours = ours(kind);
        if (cutout == PackPath.Cutout.OURS) {
            return ours;
        }

        AlphaCut iris = irisCut(programSet, kind);
        if (iris != null) {
            return iris;
        }
        return cutout == PackPath.Cutout.PACK ? null : ours;
    }

    static AlphaCuts overFrame(ProgramSet programSet) {
        return new AlphaCuts(irisCutOrOurs(programSet, PackProgram.Kind.OPAQUE),
                irisCutOrOurs(programSet, PackProgram.Kind.TRANSLUCENT));
    }

    private static AlphaCut irisCutOrOurs(ProgramSet programSet, PackProgram.Kind kind) {
        AlphaCut iris = irisCut(programSet, kind);
        return iris != null ? iris : ours(kind);
    }

    private static AlphaCut ours(PackProgram.Kind kind) {
        return kind == PackProgram.Kind.TRANSLUCENT || kind == PackProgram.Kind.SHADOW_TRANSLUCENT
                ? AlphaCuts.OWN.translucent() : AlphaCuts.OWN.opaque();
    }

    private static @Nullable AlphaCut irisCut(ProgramSet programSet, PackProgram.Kind kind) {
        ShaderKey game = gameKey(kind);
        Optional<ProgramSource> program = new ProgramFallbackResolver(programSet).resolve(game.getProgram());
        boolean irisTests = program.flatMap(ProgramSource::getFragmentSource).map(IrisAlphaTestRule::applies)
                .orElse(true);
        AlphaTest test = irisTests ? program.flatMap(source -> source.getDirectives().getAlphaTestOverride())
                .orElse(game.getAlphaTest()) : AlphaTest.ALWAYS;
        return test.function() == AlphaTestFunction.ALWAYS ? null
                : new AlphaCut(compare(test.function()), test.reference());
    }

    private static ShaderKey gameKey(PackProgram.Kind kind) {
        return switch (kind) {
            case OPAQUE -> ShaderKey.SODIUM_TERRAIN_CUTOUT;
            case TRANSLUCENT -> ShaderKey.SODIUM_TERRAIN_TRANSLUCENT;
            case SHADOW -> ShaderKey.SHADOW_SODIUM_TERRAIN_CUTOUT;
            case SHADOW_TRANSLUCENT -> ShaderKey.SHADOW_SODIUM_TERRAIN_TRANSLUCENT;
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
