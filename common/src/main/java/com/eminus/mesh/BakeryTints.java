package com.eminus.mesh;

import com.eminus.cell.Dictionary;
import com.eminus.model.BiomeColours;

public record BakeryTints(BiomeColours colours, Dictionary<String> biomes) implements BiomeTints {
    @Override
    public boolean positional(int biomeId) {
        int index = index(biomeId);
        return index != BiomeColours.NO_BIOME && colours.positional(index);
    }

    @Override
    public int colour(int row, int biomeId, int blockX, int blockZ) {
        int index = index(biomeId);
        if (index == BiomeColours.NO_BIOME || row < 0 || row >= colours.rowCount()) {
            return NO_COLOUR;
        }

        return colours.colourAt(row, index, blockX, blockZ);
    }

    private int index(int biomeId) {
        String name = biomes.value(biomeId);
        return name == null ? BiomeColours.NO_BIOME : colours.biomeIndex(name);
    }
}
