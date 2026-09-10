package com.eminus.model;

import com.eminus.cell.FaceMask;

public final class ModelMetadata {
    public static final int PRESENT_SHIFT = 0;
    public static final int OCCLUDING_SHIFT = 6;
    public static final int OCCLUDABLE_SHIFT = 12;

    public static final int SELF_LIT = 1 << 18;
    public static final int TINTED = 1 << 19;
    public static final int TRANSLUCENT = 1 << 20;
    public static final int FLAGS = SELF_LIT | TINTED | TRANSLUCENT;

    public static int pack(int present, int occluding, int occludable, int flags) {
        return (present & FaceMask.ALL) << PRESENT_SHIFT
                | (occluding & FaceMask.ALL) << OCCLUDING_SHIFT
                | (occludable & FaceMask.ALL) << OCCLUDABLE_SHIFT
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

    public static boolean has(int word, int flag) {
        return (word & flag) != 0;
    }

    private ModelMetadata() {
    }
}
