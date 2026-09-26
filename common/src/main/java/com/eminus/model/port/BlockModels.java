package com.eminus.model.port;

import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface BlockModels {
    BlockModel model(BlockState state);

    void pick(BlockState state, RandomSource random, List<Object> parts);

    void quads(List<Object> parts, List<ModelQuad> into);

    boolean forceOpaque(BlockState state);
}
