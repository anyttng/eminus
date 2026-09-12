package com.eminus.render.far.client;

import com.eminus.Eminus;
import com.eminus.cell.Dictionary;
import com.eminus.model.BakedModel;
import com.eminus.model.BiomeColours;
import com.eminus.model.ModelBakery;
import com.eminus.model.client.ModelAtlas;
import com.eminus.model.client.ModelRecords;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.color.block.BlockTintSource;

public final class ModelPublisher implements AutoCloseable {
    public static final int START_CELLS = 4;
    public static final int START_RECORDS = 1024;

    private static final int GROWTH = 2;

    private final ModelBakery bakery;
    private final BiomeColours colours;
    private final Dictionary<String> biomes;
    private final ModelAtlas atlas;
    private final TintTable tints;

    private ModelRecords records;
    private int recordCapacity = START_RECORDS;
    private int published;
    private boolean atlasFull;
    private boolean tintsFull;

    private ModelPublisher(ModelBakery bakery, BiomeColours colours, Dictionary<String> biomes,
            ModelAtlas atlas, TintTable tints, ModelRecords records) {
        this.bakery = bakery;
        this.colours = colours;
        this.biomes = biomes;
        this.atlas = atlas;
        this.tints = tints;
        this.records = records;
    }

    public static ModelPublisher start(ModelBakery bakery, BiomeColours colours, Dictionary<String> biomes) {
        RenderSystem.assertOnRenderThread();
        return new ModelPublisher(bakery, colours, biomes, ModelAtlas.create(START_CELLS), TintTable.create(),
                ModelRecords.create(START_RECORDS));
    }

    public ModelAtlas atlas() {
        return atlas;
    }

    public ModelRecords records() {
        return records;
    }

    public TintTable tints() {
        return tints;
    }

    public int published() {
        return published;
    }

    public void publish() {
        RenderSystem.assertOnRenderThread();
        tints.appendBiomes(colours, biomes);

        int baked = bakery.modelCount();
        if (baked > recordCapacity) {
            growRecords(baked);
        }

        while (published < baked && fitAtlas(published)) {
            BakedModel model = bakery.model(published);
            atlas.upload(published, model);
            records.write(published, model, tintRow(model));
            published++;
        }
    }

    @Override
    public void close() {
        records.close();
        tints.close();
        atlas.close();
    }

    private boolean fitAtlas(int modelId) {
        while (!atlas.fits(modelId)) {
            if (atlas.grow(bakery) == 0) {
                if (!atlasFull) {
                    Eminus.LOGGER.error("The model atlas is full at {} models; the rest stay unpublished", published);
                    atlasFull = true;
                }

                return false;
            }
        }

        return true;
    }

    private void growRecords(int wanted) {
        int grown = recordCapacity;
        while (grown < wanted) {
            grown *= GROWTH;
        }

        records.close();
        records = ModelRecords.create(grown);
        recordCapacity = grown;

        for (int modelId = 0; modelId < published; modelId++) {
            BakedModel model = bakery.model(modelId);
            records.write(modelId, model, tintRow(model));
        }
    }

    private int tintRow(BakedModel model) {
        BlockTintSource tint = model.tint();
        int row = colours.row(tint);

        if (row == BiomeColours.NO_ROW) {
            return BiomeColours.NO_ROW;
        }

        if (!TintTable.fits(row)) {
            if (!tintsFull) {
                Eminus.LOGGER.warn("More than {} tint sources are baked; the rest draw untinted", TintTable.MAX_ROWS);
                tintsFull = true;
            }

            return BiomeColours.NO_ROW;
        }

        if (!tints.holds(row)) {
            tints.writeRow(row, tint, colours, biomes);
        }

        return row;
    }
}
