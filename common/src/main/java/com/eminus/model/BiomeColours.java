package com.eminus.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

public final class BiomeColours {
    public static final int NO_COLOUR = -1;
    public static final int NO_ROW = -1;
    public static final int MAX_ROWS = 8;

    private static final BlockPos SAMPLE = BlockPos.ZERO;
    private static final int RGB_MASK = 0x00FF_FFFF;

    public record Colours(int[] values) implements Comparable<Colours> {
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
    private final List<BlockAndTintGetter> levels;

    private volatile List<Colours> rows = List.of();
    private volatile Map<Colours, Integer> rowByColours = Map.of();

    public BiomeColours(Map<String, BlockAndTintGetter> levels) {
        List<String> biomes = levels.keySet().stream().sorted().toList();
        for (int index = 0; index < biomes.size(); index++) {
            biomeIndex.put(biomes.get(index), index);
        }

        this.levels = biomes.stream().map(levels::get).toList();
    }

    public Colours sample(BlockTintSource tint, BlockState state) {
        int[] values = new int[levels.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = tint.colorInWorld(state, levels.get(index), SAMPLE) & RGB_MASK;
        }

        return new Colours(values);
    }

    public @Nullable Tint resolve(@Nullable BlockTintSource tint, BlockState state) {
        if (tint == null) {
            return null;
        }

        if (levels.isEmpty()) {
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
        if (ranked.size() > MAX_ROWS) {
            throw new IllegalArgumentException("At most " + MAX_ROWS + " tint rows, got " + ranked.size());
        }

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

    public int biomeCount() {
        return levels.size();
    }

    public int rowCount() {
        return rows.size();
    }
}
