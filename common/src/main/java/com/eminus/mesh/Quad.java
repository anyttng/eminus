package com.eminus.mesh;

import com.eminus.cell.DetailLevel;

public final class Quad {
    public static final int MAX_SIDE = 16;
    public static final int FIRST_BLADE_FACE = 6;
    public static final int BLADE_COUNT = 2;
    public static final int FACE_BITS = 3;
    public static final int COORDINATE_BITS = DetailLevel.SIDE_BITS;
    public static final int SIDE_BITS = 4;
    public static final int LIGHT_BITS = 8;
    public static final int MODEL_BITS = 18;
    public static final int COLOUR_BITS = 12;
    public static final int MAX_MODEL_ID = (1 << MODEL_BITS) - 1;
    public static final int MAX_COLOUR_INDEX = (1 << COLOUR_BITS) - 1;

    public static final int FACE_SHIFT = 0;
    public static final int X_SHIFT = FACE_SHIFT + FACE_BITS;
    public static final int Y_SHIFT = X_SHIFT + COORDINATE_BITS;
    public static final int Z_SHIFT = Y_SHIFT + COORDINATE_BITS;
    public static final int WIDTH_SHIFT = Z_SHIFT + COORDINATE_BITS;
    public static final int HEIGHT_SHIFT = WIDTH_SHIFT + SIDE_BITS;
    public static final int LIGHT_SHIFT = HEIGHT_SHIFT + SIDE_BITS;
    public static final int MODEL_SHIFT = LIGHT_SHIFT + LIGHT_BITS;
    public static final int COLOUR_SHIFT = MODEL_SHIFT + MODEL_BITS;

    private static final long FACE_MASK = (1L << FACE_BITS) - 1;
    private static final long COORDINATE_MASK = (1L << COORDINATE_BITS) - 1;
    private static final long SIDE_MASK = (1L << SIDE_BITS) - 1;
    private static final long LIGHT_MASK = (1L << LIGHT_BITS) - 1;
    private static final long MODEL_MASK = MAX_MODEL_ID;
    private static final long COLOUR_MASK = MAX_COLOUR_INDEX;

    public static long data(int light, int modelId, int colourIndex) {
        return ((light & LIGHT_MASK) << LIGHT_SHIFT)
                | ((modelId & MODEL_MASK) << MODEL_SHIFT)
                | ((colourIndex & COLOUR_MASK) << COLOUR_SHIFT);
    }

    public static long of(long data, int face, int x, int y, int z, int width, int height) {
        return data
                | ((face & FACE_MASK) << FACE_SHIFT)
                | ((x & COORDINATE_MASK) << X_SHIFT)
                | ((y & COORDINATE_MASK) << Y_SHIFT)
                | ((z & COORDINATE_MASK) << Z_SHIFT)
                | (((width - 1) & SIDE_MASK) << WIDTH_SHIFT)
                | (((height - 1) & SIDE_MASK) << HEIGHT_SHIFT);
    }

    public static int bladeFace(int blade) {
        return FIRST_BLADE_FACE + blade;
    }

    public static boolean isBlade(long quad) {
        return face(quad) >= FIRST_BLADE_FACE;
    }

    public static boolean fitsModelId(int modelId) {
        return modelId >= 0 && modelId <= MAX_MODEL_ID;
    }

    public static int face(long quad) {
        return (int) ((quad >>> FACE_SHIFT) & FACE_MASK);
    }

    public static int x(long quad) {
        return (int) ((quad >>> X_SHIFT) & COORDINATE_MASK);
    }

    public static int y(long quad) {
        return (int) ((quad >>> Y_SHIFT) & COORDINATE_MASK);
    }

    public static int z(long quad) {
        return (int) ((quad >>> Z_SHIFT) & COORDINATE_MASK);
    }

    public static int width(long quad) {
        return (int) ((quad >>> WIDTH_SHIFT) & SIDE_MASK) + 1;
    }

    public static int height(long quad) {
        return (int) ((quad >>> HEIGHT_SHIFT) & SIDE_MASK) + 1;
    }

    public static int light(long quad) {
        return (int) ((quad >>> LIGHT_SHIFT) & LIGHT_MASK);
    }

    public static int modelId(long quad) {
        return (int) ((quad >>> MODEL_SHIFT) & MODEL_MASK);
    }

    public static int colourIndex(long quad) {
        return (int) ((quad >>> COLOUR_SHIFT) & COLOUR_MASK);
    }

    private Quad() {
    }
}
