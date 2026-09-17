package com.eminus.mesh;

import com.eminus.cell.DetailLevel;

public final class Quad {
    public static final int MAX_SIDE = 16;
    public static final int FIRST_BLADE_FACE = 6;
    public static final int BLADE_COUNT = 2;
    public static final int MAX_MODEL_ID = (1 << 18) - 1;
    public static final int MAX_COLOUR_INDEX = (1 << 12) - 1;

    private static final int FACE_SHIFT = 0;
    private static final int X_SHIFT = 3;
    private static final int Y_SHIFT = 8;
    private static final int Z_SHIFT = 13;
    private static final int WIDTH_SHIFT = 18;
    private static final int HEIGHT_SHIFT = 22;
    private static final int LIGHT_SHIFT = 26;
    private static final int MODEL_SHIFT = 34;
    private static final int COLOUR_SHIFT = 52;

    private static final long FACE_MASK = 0x7L;
    private static final long COORDINATE_MASK = DetailLevel.VOXELS_PER_SIDE - 1;
    private static final long SIDE_MASK = 0xFL;
    private static final long LIGHT_MASK = 0xFFL;
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
