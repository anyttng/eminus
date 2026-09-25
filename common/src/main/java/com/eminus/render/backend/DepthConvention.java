package com.eminus.render.backend;

import com.eminus.gpu.pipeline.DepthCompare;

public record DepthConvention(boolean zeroToOne, DepthCompare compare, double farthest) {
    // The game reverses depth unconditionally: Projection swaps near and far, DepthStencilState.DEFAULT tests GREATER_THAN_OR_EQUAL.
    public static final DepthCompare REVERSED_COMPARE = DepthCompare.GREATER_OR_EQUAL;
    public static final DepthCompare REVERSED_FARTHER_WINS = DepthCompare.LESS_OR_EQUAL;
    public static final double REVERSED_FARTHEST = 0.0;
    public static final double REVERSED_NEAREST = 1.0;

    public static DepthConvention of(boolean zeroToOne) {
        return new DepthConvention(zeroToOne, REVERSED_COMPARE, REVERSED_FARTHEST);
    }

    public String range() {
        return zeroToOne ? "0..1" : "-1..1";
    }
}
