package com.eminus.client.model.game;

import java.util.List;

import com.eminus.model.port.BlockModel;
import com.eminus.model.port.BlockModels;
import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.VariantDraw;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public record GameBlockModels(BlockModelShaper shaper, boolean cutoutLeaves, LoaderModels loader)
        implements BlockModels {
    @Override
    public BlockModel model(BlockState state) {
        return new GameBlockModel(state, shaper.getBlockModel(state), loader);
    }

    @Override
    public void pick(BlockState state, RandomSource random, List<Object> parts) {
        loader.quads(state, shaper.getBlockModel(state), random, parts);
    }

    @Override
    public void quads(List<Object> parts, List<ModelQuad> into) {
        GameQuads.convert(parts, into);
    }

    @Override
    public boolean forceOpaque(BlockState state) {
        return !cutoutLeaves && state.getBlock() instanceof LeavesBlock;
    }

    @Override
    public VariantDraw variantDraw() {
        return GameBlockModel.VARIANT_DRAW;
    }
}
