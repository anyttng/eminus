package com.eminus.mesh;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class FakeTints implements BiomeTints {
    private final Map<Long, Integer> colors = new HashMap<>();
    private final Set<Integer> positional = new HashSet<>();
    private final Map<Integer, Integer> constants = new HashMap<>();

    void define(int row, int biomeId, int color) {
        colors.put(key(row, biomeId), color);
    }

    void vary(int biomeId) {
        positional.add(biomeId);
    }

    void fix(int row, int color) {
        constants.put(row, color);
    }

    @Override
    public boolean positional(int biomeId) {
        return positional.contains(biomeId);
    }

    @Override
    public int constant(int row) {
        return constants.getOrDefault(row, NO_COLOR);
    }

    @Override
    public int color(int row, int biomeId, int blockX, int blockZ) {
        Integer color = colors.get(key(row, biomeId));
        if (color == null) {
            return NO_COLOR;
        }

        return positional.contains(biomeId) ? color ^ (blockX & 1) : color;
    }

    private static long key(int row, int biomeId) {
        return (long) row << Integer.SIZE | (biomeId & 0xFFFF_FFFFL);
    }
}
