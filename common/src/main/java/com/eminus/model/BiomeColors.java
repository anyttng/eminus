package com.eminus.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.eminus.model.port.TintBiome;
import com.eminus.model.port.TintSource;

import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

public final class BiomeColors {
    public static final int NO_COLOR = -1;
    public static final int NO_ROW = -1;
    public static final int NO_BIOME = -1;

    private static final int SAMPLE_COLUMN = 0;
    private static final int RGB_MASK = 0x00FF_FFFF;
    private static final int WHITE = RGB_MASK;

    @SuppressWarnings("ArrayRecordComponent")
    public record Colors(int[] values, TintSource source, BlockState state) implements Comparable<Colors> {
        public boolean uniform() {
            return Arrays.stream(values).allMatch(value -> value == values[0]);
        }

        public boolean blank() {
            return uniform() && values[0] == WHITE;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Colors colors && Arrays.equals(values, colors.values);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }

        @Override
        public int compareTo(Colors other) {
            return Arrays.compare(values, other.values);
        }
    }

    private final Map<String, Integer> biomeIndex = new HashMap<>();
    private final List<TintBiome> biomes;

    private volatile List<Colors> rows = List.of();
    private volatile Map<Colors, Integer> rowByColors = Map.of();
    private volatile int[] constants = new int[0];

    public BiomeColors(Collection<TintBiome> biomes) {
        this.biomes = biomes.stream().sorted(Comparator.comparing(TintBiome::name)).toList();
        for (int index = 0; index < this.biomes.size(); index++) {
            biomeIndex.put(this.biomes.get(index).name(), index);
        }
    }

    public Colors sample(TintSource tint, BlockState state) {
        int[] values = new int[biomes.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = tint.color(state, biomes.get(index), SAMPLE_COLUMN, SAMPLE_COLUMN) & RGB_MASK;
        }

        return new Colors(values, tint, state);
    }

    public @Nullable Tint resolve(@Nullable TintSource tint, BlockState state) {
        if (tint == null) {
            return null;
        }

        if (biomes.isEmpty()) {
            return Tint.UNTINTED;
        }

        Colors colors = sample(tint, state);
        if (colors.blank()) {
            return Tint.UNTINTED;
        }

        Integer row = rowByColors.get(colors);
        return row == null ? Tint.UNTINTED : Tint.row(row);
    }

    public void assign(List<Colors> ranked) {
        Map<Colors, Integer> byColors = new HashMap<>();
        int[] uniform = new int[ranked.size()];
        for (int row = 0; row < ranked.size(); row++) {
            Colors colors = ranked.get(row);
            byColors.put(colors, row);
            uniform[row] = colors.uniform() ? colors.values()[0] : NO_COLOR;
        }

        rows = List.copyOf(ranked);
        rowByColors = Map.copyOf(byColors);
        constants = uniform;
    }

    public int constant(int row) {
        int[] uniform = constants;
        return row < 0 || row >= uniform.length ? NO_COLOR : uniform[row];
    }

    public int color(int row, String biome) {
        Integer index = biomeIndex.get(biome);
        return index == null || row < 0 || row >= rows.size() ? NO_COLOR : rows.get(row).values()[index];
    }

    public int biomeIndex(String biome) {
        Integer index = biomeIndex.get(biome);
        return index == null ? NO_BIOME : index;
    }

    public boolean positional(int biomeIndex) {
        return biomes.get(biomeIndex).positional();
    }

    public int colorAt(int row, int biomeIndex, int blockX, int blockZ) {
        Colors colors = rows.get(row);
        TintBiome biome = biomes.get(biomeIndex);
        if (!biome.positional()) {
            return colors.values()[biomeIndex];
        }

        return colors.source().color(colors.state(), biome, blockX, blockZ) & RGB_MASK;
    }

    public int biomeCount() {
        return biomes.size();
    }

    public int rowCount() {
        return rows.size();
    }
}
