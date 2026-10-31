package com.eminus.compat.iris;

import com.eminus.client.render.far.AlphaCut;
import com.eminus.client.render.far.OpaquePass;
import com.eminus.client.render.far.TranslucentPass;

import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;

import org.jspecify.annotations.Nullable;

final class PackAlphaTest {
    static @Nullable AlphaCut cut(ProgramSet programSet, PackProgram.Kind kind, PackPath.Cutout cutout) {
        AlphaCut ours = AlphaCut.atLeast(kind == PackProgram.Kind.TRANSLUCENT
                || kind == PackProgram.Kind.SHADOW_TRANSLUCENT ? TranslucentPass.ALPHA_CUTOUT
                        : OpaquePass.ALPHA_CUTOUT);
        if (cutout == PackPath.Cutout.OURS) {
            return ours;
        }

        ShaderKey game = gameKey(kind);
        AlphaTest test = new ProgramFallbackResolver(programSet).resolve(game.getProgram())
                .flatMap(source -> source.getDirectives().getAlphaTestOverride())
                .orElse(game.getAlphaTest());
        if (test.function() == AlphaTestFunction.ALWAYS) {
            return cutout == PackPath.Cutout.PACK ? null : ours;
        }
        return new AlphaCut(compare(test.function()), test.reference());
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
