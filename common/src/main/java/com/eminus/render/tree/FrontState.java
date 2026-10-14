package com.eminus.render.tree;

public record FrontState(
        int blocks,
        int level,
        int x,
        int y,
        int z,
        String place,
        long buildMillis,
        int hiddenBehind,
        int hiddenUnder,
        int hiddenUnderOutOfView) {
    public static final int NO_FRONT = -1;
    public static final String NONE = "none";
    public static final String UNREQUESTED = "unrequested";
    public static final String BUILDING = "building";
    public static final String FAILED = "failed";

    static FrontState none(int hiddenBehind, int hiddenUnder, int hiddenUnderOutOfView) {
        return new FrontState(NO_FRONT, NO_FRONT, 0, 0, 0, NONE, 0L, hiddenBehind, hiddenUnder,
                hiddenUnderOutOfView);
    }
}
