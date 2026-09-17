package com.eminus.handoff;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.OptionsRenderState;

public final class NearFieldOverride {
    public static final float NO_FOG = Float.MAX_VALUE;
    public static final double NO_FADE_IN = 0.0;

    private static boolean applied;

    public static void apply(FogData fog, OptionsRenderState options, boolean clearAtmosphericFog) {
        fog.renderDistanceStart = NO_FOG;
        fog.renderDistanceEnd = NO_FOG;
        if (clearAtmosphericFog) {
            fog.environmentalStart = NO_FOG;
            fog.environmentalEnd = NO_FOG;
        }

        options.chunkSectionFadeInTime = NO_FADE_IN;
        applied = true;
    }

    public static void skip() {
        applied = false;
    }

    // The last extract's decision, because work inside the next extract runs before the override is written again.
    public static boolean applied() {
        return applied;
    }

    private NearFieldOverride() {
    }
}
