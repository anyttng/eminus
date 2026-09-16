package com.eminus.model;

import net.minecraft.util.ARGB;

public record Tint(int row, int colour) {
    private static final int WHITE = 0xFFFF_FFFF;

    public static final Tint UNTINTED = new Tint(BiomeColours.NO_ROW, WHITE);

    public static Tint row(int row) {
        return new Tint(row, WHITE);
    }

    public static Tint constant(int colour) {
        return new Tint(BiomeColours.NO_ROW, colour);
    }

    public boolean hasRow() {
        return row != BiomeColours.NO_ROW;
    }

    public void apply(int[] faces, long[] tintMask) {
        if (hasRow()) {
            return;
        }

        int opaque = ARGB.opaque(colour);
        for (int index = 0; index < faces.length; index++) {
            if (BakedModel.tinted(tintMask, index)) {
                faces[index] = ARGB.multiply(faces[index], opaque);
                BakedModel.mark(tintMask, index, false);
            }
        }
    }
}
