package com.eminus.client.model.game;

import java.util.ArrayList;
import java.util.List;

import com.eminus.model.port.BlockModel;
import com.eminus.model.port.BlockModels;
import com.eminus.model.port.BlockTints;
import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.TintSource;
import com.eminus.model.port.Variant;

import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public record GameTints(BlockColors colors, BlockModels models) implements BlockTints {
    private static final long SAMPLE_SEED = 0L;

    @Override
    public List<TintSource> sources(BlockState state) {
        IntSortedSet layers = new IntRBTreeSet();
        BlockModel model = models.model(state);
        collectLayers(model, layers);
        for (Variant variant : model.variants()) {
            collectLayers(variant.model(), layers);
        }

        List<TintSource> sources = new ArrayList<>(layers.size());
        for (int layer : layers) {
            sources.add(new GameTintSource(colors, layer));
        }

        return sources;
    }

    @Override
    public TintSource source(BlockState state, int layer) {
        return new GameTintSource(colors, layer);
    }

    private static void collectLayers(BlockModel model, IntSortedSet layers) {
        List<ModelQuad> quads = new ArrayList<>();
        model.quads(RandomSource.create(SAMPLE_SEED), quads);
        for (ModelQuad quad : quads) {
            if (quad.tinted()) {
                layers.add(quad.tintLayer());
            }
        }
    }
}
