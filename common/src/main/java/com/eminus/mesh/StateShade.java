package com.eminus.mesh;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class StateShade {
    public static final int DARK = 1;
    public static final int PERMEABLE = 1 << 1;
    public static final int FULL_COLLISION = 1 << 2;
    public static final int OPEN = PERMEABLE;

    private static final float LIT = 1.0F;

    public static int of(BlockState state) {
        return (state.getShadeBrightness(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < LIT ? DARK : 0)
                | (!state.isViewBlocking(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                        || state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == 0 ? PERMEABLE : 0)
                | (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? FULL_COLLISION : 0);
    }

    public static boolean has(int shade, int flag) {
        return (shade & flag) != 0;
    }

    private StateShade() {
    }
}
