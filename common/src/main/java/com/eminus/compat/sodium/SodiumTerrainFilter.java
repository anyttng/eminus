package com.eminus.compat.sodium;

import com.eminus.client.frame.TerrainFilter;

public final class SodiumTerrainFilter {
    public static TerrainFilter read() {
        return SodiumMixinPlugin.sodiumPresent() ? SodiumPixelFilter.read() : TerrainFilter.LINEAR;
    }

    private SodiumTerrainFilter() {
    }
}
