package com.eminus.mesh;

import net.minecraft.core.Direction;

public final class FacePlacement {
    public static final float FLOOR = 0.0F;
    public static final float CEILING = 1.0F;

    public static float low(float model, int lowGap, int blocks) {
        return (lowGap + model) / blocks;
    }

    public static float high(float model, int highGap, int blocks) {
        return (blocks - 1 - highGap + model) / blocks;
    }

    public static float depth(Direction face, float inset, int lowGap, int highGap, int blocks, boolean inward) {
        if (face == Direction.DOWN) {
            return low(inset, lowGap, blocks);
        }

        if (face == Direction.UP) {
            return high(CEILING - inset, highGap, blocks);
        }

        float side = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? CEILING - inset : inset;
        return inward ? CEILING - side : side;
    }

    private FacePlacement() {
    }
}
