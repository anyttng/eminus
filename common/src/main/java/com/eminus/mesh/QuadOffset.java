package com.eminus.mesh;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class QuadOffset {
    public static final int NONE = 0;

    private static final int AXIS_BITS = 10;
    private static final int AXIS_MASK = (1 << AXIS_BITS) - 1;
    private static final int X_SHIFT = 0;
    private static final int Y_SHIFT = AXIS_BITS;
    private static final int Z_SHIFT = 2 * AXIS_BITS;
    private static final int SIGN_SHIFT = Integer.SIZE - AXIS_BITS;
    private static final int MAX_STEPS = (1 << (AXIS_BITS - 1)) - 1;
    private static final float STEPS_PER_BLOCK = 256.0F;

    public static int of(BlockState state, int blockX, int blockY, int blockZ) {
        if (!state.hasOffsetFunction()) {
            return NONE;
        }

        Vec3 offset = state.getOffset(new BlockPos(blockX, blockY, blockZ));
        return pack(offset.x, offset.y, offset.z);
    }

    public static int pack(double x, double y, double z) {
        return steps(x) << X_SHIFT | steps(y) << Y_SHIFT | steps(z) << Z_SHIFT;
    }

    public static float x(int offset) {
        return axis(offset, X_SHIFT);
    }

    public static float y(int offset) {
        return axis(offset, Y_SHIFT);
    }

    public static float z(int offset) {
        return axis(offset, Z_SHIFT);
    }

    private static int steps(double blocks) {
        return Math.clamp(Math.round(blocks * STEPS_PER_BLOCK), -MAX_STEPS, MAX_STEPS) & AXIS_MASK;
    }

    private static float axis(int offset, int shift) {
        return ((offset >>> shift & AXIS_MASK) << SIGN_SHIFT >> SIGN_SHIFT) / STEPS_PER_BLOCK;
    }

    private QuadOffset() {
    }
}
