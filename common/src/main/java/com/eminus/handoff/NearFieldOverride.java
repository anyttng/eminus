package com.eminus.handoff;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.OptionsRenderState;

public final class NearFieldOverride {
    public static final float NO_RENDER_DISTANCE_FOG = Float.MAX_VALUE;
    public static final double NO_FADE_IN = 0.0;

    public static void apply(FogData fog, OptionsRenderState options) {
        fog.renderDistanceStart = NO_RENDER_DISTANCE_FOG;
        fog.renderDistanceEnd = NO_RENDER_DISTANCE_FOG;
        options.chunkSectionFadeInTime = NO_FADE_IN;
    }

    private NearFieldOverride() {
    }
}
