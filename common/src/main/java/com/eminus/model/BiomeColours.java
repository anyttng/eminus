package com.eminus.model;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class BiomeColours {
    public static final int NO_COLOUR = -1;
    public static final int NO_ROW = -1;

    private static final BlockPos SAMPLE = BlockPos.ZERO;

    public record Row(int index, Map<String, Integer> colours) {
    }

    private final Map<String, BlockAndTintGetter> levels;

    private volatile Map<BlockTintSource, Row> rows = new IdentityHashMap<>();

    public BiomeColours(Map<String, BlockAndTintGetter> levels) {
        this.levels = Map.copyOf(levels);
    }

    public void fill(BlockTintSource tint, BlockState state) {
        if (rows.containsKey(tint)) {
            return;
        }

        Map<String, Integer> colours = new HashMap<>();
        levels.forEach((biome, level) -> colours.put(biome, tint.colorInWorld(state, level, SAMPLE)));

        Map<BlockTintSource, Row> next = new IdentityHashMap<>(rows);
        next.put(tint, new Row(next.size(), Map.copyOf(colours)));
        rows = next;
    }

    public int row(BlockTintSource tint) {
        Row row = tint == null ? null : rows.get(tint);
        return row == null ? NO_ROW : row.index();
    }

    public int colour(BlockTintSource tint, String biome) {
        Row row = rows.get(tint);
        Integer colour = row == null ? null : row.colours().get(biome);
        return colour == null ? NO_COLOUR : colour;
    }

    public int biomeCount() {
        return levels.size();
    }

    public int tintCount() {
        return rows.size();
    }
}
