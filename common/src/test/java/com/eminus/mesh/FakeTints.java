package com.eminus.mesh;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class FakeTints implements BiomeTints {
    private final Map<Long, Integer> colours = new HashMap<>();
    private final Set<Integer> positional = new HashSet<>();
    private final Map<Integer, Integer> constants = new HashMap<>();

    void define(int row, int biomeId, int colour) {
        colours.put(key(row, biomeId), colour);
    }

    void vary(int biomeId) {
        positional.add(biomeId);
    }

    void fix(int row, int colour) {
        constants.put(row, colour);
    }

    @Override
    public boolean positional(int biomeId) {
        return positional.contains(biomeId);
    }

    @Override
    public int constant(int row) {
        return constants.getOrDefault(row, NO_COLOUR);
    }

    @Override
    public int colour(int row, int biomeId, int blockX, int blockZ) {
        Integer colour = colours.get(key(row, biomeId));
        if (colour == null) {
            return NO_COLOUR;
        }

        return positional.contains(biomeId) ? colour ^ (blockX & 1) : colour;
    }

    private static long key(int row, int biomeId) {
        return (long) row << Integer.SIZE | (biomeId & 0xFFFF_FFFFL);
    }
}
