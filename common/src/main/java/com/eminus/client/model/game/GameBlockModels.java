package com.eminus.client.model.game;

import java.util.List;

import com.eminus.model.port.BlockModel;
import com.eminus.model.port.BlockModels;
import com.eminus.model.port.ModelQuad;

import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public record GameBlockModels(BlockStateModelSet models, boolean cutoutLeaves) implements BlockModels {
    @Override
    public BlockModel model(BlockState state) {
        return new GameBlockModel(models.get(state));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void pick(BlockState state, RandomSource random, List<Object> parts) {
        models.get(state).collectParts(random, (List<BlockStateModelPart>) (List) parts);
    }

    @Override
    public void quads(List<Object> parts, List<ModelQuad> into) {
        GameQuads.gather(parts, into);
    }

    @Override
    public boolean forceOpaque(BlockState state) {
        return ModelBlockRenderer.forceOpaque(cutoutLeaves, state);
    }
}
