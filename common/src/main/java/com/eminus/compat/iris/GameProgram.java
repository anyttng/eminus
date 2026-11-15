package com.eminus.compat.iris;

import java.util.Optional;

import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTests;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

final class GameProgram {
    static ProgramId id(PackProgram.Kind kind) {
        return switch (kind) {
            case OPAQUE -> ProgramId.TerrainCutout;
            case TRANSLUCENT -> ProgramId.Water;
            case SHADOW -> ProgramId.ShadowCutout;
            case SHADOW_TRANSLUCENT -> ProgramId.ShadowWater;
            case BEAM, BEAM_GLOW -> ProgramId.BeaconBeam;
        };
    }

    static AlphaTest alphaTest(PackProgram.Kind kind) {
        return switch (kind) {
            case OPAQUE, SHADOW -> AlphaTests.ONE_TENTH_ALPHA;
            case TRANSLUCENT, SHADOW_TRANSLUCENT -> AlphaTest.ALWAYS;
            case BEAM, BEAM_GLOW -> throw new IllegalArgumentException("A beam program cuts in its own pass");
        };
    }

    static Optional<ProgramSource> resolve(ProgramSet programSet, PackProgram.Kind kind) {
        return new ProgramFallbackResolver(programSet).resolve(id(kind));
    }

    private GameProgram() {
    }
}
