package com.eminus.client.model.game;

import java.util.ArrayList;
import java.util.List;

import com.eminus.mixin.WeightedVariantsAccessor;
import com.eminus.model.port.BlockModel;
import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.Variant;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;

record GameBlockModel(BlockStateModel model) implements BlockModel {
    @Override
    public void quads(RandomSource random, List<ModelQuad> into) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        GameQuads.gather(parts, into);
    }

    @Override
    public List<Variant> variants() {
        if (!(model instanceof WeightedVariants weighted)) {
            return List.of();
        }

        List<Weighted<BlockStateModel>> entries = ((WeightedVariantsAccessor) weighted).eminus$list().unwrap();
        List<Variant> variants = new ArrayList<>(entries.size());
        for (Weighted<BlockStateModel> entry : entries) {
            variants.add(new Variant(new GameBlockModel(entry.value()), entry.weight()));
        }

        return variants;
    }
}
