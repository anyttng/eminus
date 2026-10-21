package com.eminus.compat.sodium;

import com.eminus.client.frame.TerrainFilter;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;

// Loaded only once the gate admitted Sodium: it names Sodium's classes.
final class SodiumPixelFilter {
    static TerrainFilter read() {
        return SodiumClientMod.options().quality.pixelFilteringMode == FilterMode.NEAREST ? TerrainFilter.NEAREST
                : TerrainFilter.LINEAR;
    }

    private SodiumPixelFilter() {
    }
}
