package com.eminus.model;

import com.eminus.model.port.TintBiome;

record FakeBiome(int index, boolean positional) implements TintBiome {
    private static final String PREFIX = "test:biome_";

    FakeBiome(int index) {
        this(index, false);
    }

    static String name(int index) {
        return PREFIX + index;
    }

    @Override
    public String name() {
        return name(index);
    }
}
