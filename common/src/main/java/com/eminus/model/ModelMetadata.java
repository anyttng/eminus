package com.eminus.model;

import com.eminus.cell.FaceMask;

public final class ModelMetadata {
    public static final int PRESENT_SHIFT = 0;
    public static final int OCCLUDING_SHIFT = 6;
    public static final int OCCLUDABLE_SHIFT = 12;
    public static final int EMISSION_SHIFT = 18;

    public static final int MAX_EMISSION = 15;

    public static final int TINTED = 1 << 22;
    public static final int TRANSLUCENT = 1 << 23;
    public static final int BLADED = 1 << 24;
    public static final int SLOPED = 1 << 25;
    public static final int FLAGS = TINTED | TRANSLUCENT | BLADED | SLOPED;

    public static int pack(int present, int occluding, int occludable, int emission, int flags) {
        return (present & FaceMask.ALL) << PRESENT_SHIFT
                | (occluding & FaceMask.ALL) << OCCLUDING_SHIFT
                | (occludable & FaceMask.ALL) << OCCLUDABLE_SHIFT
                | (emission & MAX_EMISSION) << EMISSION_SHIFT
                | flags & FLAGS;
    }

    public static int present(int word) {
        return word >>> PRESENT_SHIFT & FaceMask.ALL;
    }

    public static int occluding(int word) {
        return word >>> OCCLUDING_SHIFT & FaceMask.ALL;
    }

    public static int occludable(int word) {
        return word >>> OCCLUDABLE_SHIFT & FaceMask.ALL;
    }

    public static int emission(int word) {
        return word >>> EMISSION_SHIFT & MAX_EMISSION;
    }

    public static int withEmission(int word, int emission) {
        return word & ~(MAX_EMISSION << EMISSION_SHIFT) | (emission & MAX_EMISSION) << EMISSION_SHIFT;
    }

    public static boolean has(int word, int flag) {
        return (word & flag) != 0;
    }

    private ModelMetadata() {
    }
}
