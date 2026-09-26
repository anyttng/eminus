package com.eminus.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.client.renderer.fog.FogData;

import org.junit.jupiter.api.Test;

class NearFieldOverrideTest {
    private static final boolean CLEAR_ATMOSPHERIC_FOG = true;
    private static final boolean KEEP_ATMOSPHERIC_FOG = false;
    private static final float ENVIRONMENTAL_START = 10.0F;
    private static final float ENVIRONMENTAL_END = 1024.0F;
    private static final float RENDER_DISTANCE_START = 128.0F;
    private static final float RENDER_DISTANCE_END = 192.0F;
    private static final float SKY_END = 192.0F;
    private static final float CLOUD_END = 176.0F;
    private static final double FADE_IN_SECONDS = 0.5;

    @Test
    void theRenderDistanceFogGoesToInfinityAndTheFadeInToZero() {
        FogData fog = fogData();

        NearFieldOverride.apply(fog, KEEP_ATMOSPHERIC_FOG);

        assertEquals(NearFieldOverride.NO_FOG, fog.renderDistanceStart);
        assertEquals(NearFieldOverride.NO_FOG, fog.renderDistanceEnd);
        assertEquals(NearFieldOverride.NO_FADE_IN, NearFieldOverride.fadeInTime(FADE_IN_SECONDS));
    }

    @Test
    void aSkippedOverrideLeavesTheFadeInAtTheOption() {
        NearFieldOverride.apply(fogData(), KEEP_ATMOSPHERIC_FOG);

        NearFieldOverride.skip();

        assertEquals(FADE_IN_SECONDS, NearFieldOverride.fadeInTime(FADE_IN_SECONDS));
    }

    @Test
    void keptAtmosphericFogLeavesTheEnvironmentalFogTheSkyAndTheCloudsAlone() {
        FogData fog = fogData();

        NearFieldOverride.apply(fog, KEEP_ATMOSPHERIC_FOG);

        assertEquals(ENVIRONMENTAL_START, fog.environmentalStart);
        assertEquals(ENVIRONMENTAL_END, fog.environmentalEnd);
        assertEquals(SKY_END, fog.skyEnd);
        assertEquals(CLOUD_END, fog.cloudEnd);
    }

    @Test
    void clearedAtmosphericFogSendsTheEnvironmentalFogToInfinityAndLeavesTheSkyAndCloudsAlone() {
        FogData fog = fogData();

        NearFieldOverride.apply(fog, CLEAR_ATMOSPHERIC_FOG);

        assertEquals(NearFieldOverride.NO_FOG, fog.environmentalStart);
        assertEquals(NearFieldOverride.NO_FOG, fog.environmentalEnd);
        assertEquals(NearFieldOverride.NO_FOG, fog.renderDistanceStart);
        assertEquals(NearFieldOverride.NO_FOG, fog.renderDistanceEnd);
        assertEquals(SKY_END, fog.skyEnd);
        assertEquals(CLOUD_END, fog.cloudEnd);
    }

    private static FogData fogData() {
        FogData fog = new FogData();
        fog.environmentalStart = ENVIRONMENTAL_START;
        fog.environmentalEnd = ENVIRONMENTAL_END;
        fog.renderDistanceStart = RENDER_DISTANCE_START;
        fog.renderDistanceEnd = RENDER_DISTANCE_END;
        fog.skyEnd = SKY_END;
        fog.cloudEnd = CLOUD_END;
        return fog;
    }
}
