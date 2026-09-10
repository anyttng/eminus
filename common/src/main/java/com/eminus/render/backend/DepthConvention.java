package com.eminus.render.backend;

import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.DeviceInfo;

public record DepthConvention(boolean zeroToOne, CompareOp compare, double farthest) {
    // The game reverses depth unconditionally: Projection swaps near and far, DepthStencilState.DEFAULT tests GREATER_THAN_OR_EQUAL.
    public static final CompareOp REVERSED_COMPARE = CompareOp.GREATER_THAN_OR_EQUAL;
    public static final double REVERSED_FARTHEST = 0.0;

    public static DepthConvention of(DeviceInfo info) {
        return new DepthConvention(info.isZZeroToOne(), REVERSED_COMPARE, REVERSED_FARTHEST);
    }

    public String range() {
        return zeroToOne ? "0..1" : "-1..1";
    }
}
