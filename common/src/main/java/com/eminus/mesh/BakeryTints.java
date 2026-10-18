package com.eminus.mesh;

import com.eminus.cell.Dictionary;
import com.eminus.model.BiomeColors;

public record BakeryTints(BiomeColors colors, Dictionary<String> biomes) implements BiomeTints {
    @Override
    public boolean positional(int biomeId) {
        int index = index(biomeId);
        return index != BiomeColors.NO_BIOME && colors.positional(index);
    }

    @Override
    public int constant(int row) {
        return colors.constant(row);
    }

    @Override
    public int color(int row, int biomeId, int blockX, int blockZ) {
        int index = index(biomeId);
        if (index == BiomeColors.NO_BIOME || row < 0 || row >= colors.rowCount()) {
            return NO_COLOR;
        }

        return colors.colorAt(row, index, blockX, blockZ);
    }

    private int index(int biomeId) {
        String name = biomes.value(biomeId);
        return name == null ? BiomeColors.NO_BIOME : colors.biomeIndex(name);
    }
}
