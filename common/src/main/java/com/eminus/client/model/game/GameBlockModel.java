package com.eminus.client.model.game;

import java.util.ArrayList;
import java.util.List;

import com.eminus.mixin.WeightedVariantsAccessor;
import com.eminus.model.port.BlockModel;
import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.Variant;
import com.eminus.model.port.VariantDraw;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.block.state.BlockState;

record GameBlockModel(BlockState state, BakedModel model, LoaderModels loader) implements BlockModel {
    static final VariantDraw VARIANT_DRAW = VariantDraw.NEXT_LONG_MODULO;

    @Override
    public void quads(RandomSource random, List<ModelQuad> into) {
        List<LayeredQuad> quads = new ArrayList<>();
        loader.quads(state, model, random, quads);
        GameQuads.convert(quads, into);
    }

    @Override
    public List<Variant> variants() {
        if (!(model instanceof WeightedBakedModel weighted)) {
            return List.of();
        }

        List<WeightedEntry.Wrapper<BakedModel>> entries = ((WeightedVariantsAccessor) weighted).eminus$list();
        List<Variant> variants = new ArrayList<>(entries.size());
        for (WeightedEntry.Wrapper<BakedModel> entry : entries) {
            variants.add(new Variant(new GameBlockModel(state, entry.data(), loader), entry.weight().asInt()));
        }

        return variants;
    }
}
