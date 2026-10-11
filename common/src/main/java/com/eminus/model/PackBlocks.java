package com.eminus.model;

import java.util.function.ToIntFunction;

import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.ints.IntSet;

public record PackBlocks(ToIntFunction<BlockState> ids, IntSet moved) {
    public static final PackBlocks NONE = new PackBlocks(state -> BakedModel.NO_PACK_ID, IntSet.of());
}
