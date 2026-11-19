package com.eminus.model;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

final class SeedOverrides {
    private static final BlockPos[] PROBES = {
        new BlockPos(1, 2, 3),
        new BlockPos(-37, 71, 113),
        new BlockPos(1024, -48, -517),
    };

    @SuppressWarnings("deprecation")
    static boolean overridden(BlockState state) {
        for (BlockPos probe : PROBES) {
            if (state.getSeed(probe) != Mth.getSeed(probe)) {
                return true;
            }
        }

        return false;
    }

    private SeedOverrides() {
    }
}
