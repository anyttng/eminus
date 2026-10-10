package com.eminus.model;

import java.util.Arrays;

public record Tint(int row) {
    public static final Tint UNTINTED = new Tint(BiomeColours.NO_ROW);

    public static Tint row(int row) {
        return new Tint(row);
    }

    public boolean hasRow() {
        return row != BiomeColours.NO_ROW;
    }

    public void apply(long[] tintMask) {
        if (!hasRow()) {
            Arrays.fill(tintMask, 0L);
        }
    }
}
