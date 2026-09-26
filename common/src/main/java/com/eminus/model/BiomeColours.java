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

public final class BiomeColours {
    public static final int NO_COLOUR = -1;
    public static final int NO_ROW = -1;
    public static final int NO_BIOME = -1;

    private static final int SAMPLE_COLUMN = 0;
    private static final int RGB_MASK = 0x00FF_FFFF;

    public record Colours(int[] values, TintSource source, BlockState state) implements Comparable<Colours> {
        public boolean uniform() {
            return Arrays.stream(values).allMatch(value -> value == values[0]);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Colours colours && Arrays.equals(values, colours.values);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }

        @Override
        public int compareTo(Colours other) {
            return Arrays.compare(values, other.values);
        }
    }

    private final Map<String, Integer> biomeIndex = new HashMap<>();
    private final List<TintBiome> biomes;

    private volatile List<Colours> rows = List.of();
    private volatile Map<Colours, Integer> rowByColours = Map.of();

    public BiomeColours(Collection<TintBiome> biomes) {
        this.biomes = biomes.stream().sorted(Comparator.comparing(TintBiome::name)).toList();
        for (int index = 0; index < this.biomes.size(); index++) {
            biomeIndex.put(this.biomes.get(index).name(), index);
        }
    }

    public Colours sample(TintSource tint, BlockState state) {
        int[] values = new int[biomes.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = tint.colour(state, biomes.get(index), SAMPLE_COLUMN, SAMPLE_COLUMN) & RGB_MASK;
        }

        return new Colours(values, tint, state);
    }

    public @Nullable Tint resolve(@Nullable TintSource tint, BlockState state) {
        if (tint == null) {
            return null;
        }

        if (biomes.isEmpty()) {
            return Tint.UNTINTED;
        }

        Colours colours = sample(tint, state);
        if (colours.uniform()) {
            return Tint.constant(colours.values()[0]);
        }

        Integer row = rowByColours.get(colours);
        return row == null ? Tint.UNTINTED : Tint.row(row);
    }

    public void assign(List<Colours> ranked) {
        Map<Colours, Integer> byColours = new HashMap<>();
        for (int row = 0; row < ranked.size(); row++) {
            byColours.put(ranked.get(row), row);
        }

        rows = List.copyOf(ranked);
        rowByColours = Map.copyOf(byColours);
    }

    public int colour(int row, String biome) {
        Integer index = biomeIndex.get(biome);
        return index == null || row < 0 || row >= rows.size() ? NO_COLOUR : rows.get(row).values()[index];
    }

    public int biomeIndex(String biome) {
        Integer index = biomeIndex.get(biome);
        return index == null ? NO_BIOME : index;
    }

    public boolean positional(int biomeIndex) {
        return biomes.get(biomeIndex).positional();
    }

    public int colourAt(int row, int biomeIndex, int blockX, int blockZ) {
        Colours colours = rows.get(row);
        TintBiome biome = biomes.get(biomeIndex);
        if (!biome.positional()) {
            return colours.values()[biomeIndex];
        }

        return colours.source().colour(colours.state(), biome, blockX, blockZ) & RGB_MASK;
    }

    public int biomeCount() {
        return biomes.size();
    }

    public int rowCount() {
        return rows.size();
    }
}
