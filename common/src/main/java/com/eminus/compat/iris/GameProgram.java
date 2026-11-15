package com.eminus.compat.iris;

import java.util.Optional;

import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

final class GameProgram {
    static ShaderKey key(PackProgram.Kind kind) {
        return switch (kind) {
            case OPAQUE -> ShaderKey.SODIUM_TERRAIN_CUTOUT;
            case TRANSLUCENT -> ShaderKey.SODIUM_TERRAIN_TRANSLUCENT;
            case SHADOW -> ShaderKey.SHADOW_SODIUM_TERRAIN_CUTOUT;
            case SHADOW_TRANSLUCENT -> ShaderKey.SHADOW_SODIUM_TERRAIN_TRANSLUCENT;
            case BEAM, BEAM_GLOW -> ShaderKey.BEACON;
        };
    }

    static Optional<ProgramSource> resolve(ProgramSet programSet, PackProgram.Kind kind) {
        return new ProgramFallbackResolver(programSet).resolve(key(kind).getProgram());
    }

    private GameProgram() {
    }
}
