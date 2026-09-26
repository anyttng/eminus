package com.eminus.model.port;

import java.util.List;

import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

public interface BlockTints {
    List<TintSource> sources(BlockState state);

    @Nullable TintSource source(BlockState state, int layer);
}
