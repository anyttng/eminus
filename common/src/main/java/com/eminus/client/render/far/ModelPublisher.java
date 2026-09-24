package com.eminus.client.render.far;

import com.eminus.Eminus;
import com.eminus.model.BakedModel;
import com.eminus.model.ModelBakery;
import com.eminus.client.model.ModelAtlas;
import com.eminus.client.model.ModelRecords;
import com.eminus.client.model.ModelVariants;
import com.eminus.gpu.Gpu;

import com.mojang.blaze3d.systems.RenderSystem;

public final class ModelPublisher implements AutoCloseable {
    public static final int START_CELLS = 4;
    public static final int START_RECORDS = 1024;
    public static final int START_VARIANTS = 256;

    private static final int GROWTH = 2;

    private final ModelBakery bakery;
    private final ModelAtlas atlas;

    private ModelRecords records;
    private ModelVariants variants;
    private int recordCapacity = START_RECORDS;
    private int published;
    private int variantEntries;
    private boolean atlasFull;

    private ModelPublisher(ModelBakery bakery, ModelAtlas atlas, ModelRecords records, ModelVariants variants) {
        this.bakery = bakery;
        this.atlas = atlas;
        this.records = records;
        this.variants = variants;
    }

    public static ModelPublisher start(Gpu gpu, ModelBakery bakery) {
        RenderSystem.assertOnRenderThread();
        return new ModelPublisher(bakery, ModelAtlas.create(gpu, START_CELLS), ModelRecords.create(START_RECORDS),
                ModelVariants.create(START_VARIANTS));
    }

    public ModelAtlas atlas() {
        return atlas;
    }

    public ModelRecords records() {
        return records;
    }

    public ModelVariants variants() {
        return variants;
    }

    public int published() {
        return published;
    }

    public void publish() {
        RenderSystem.assertOnRenderThread();
        int baked = bakery.modelCount();
        if (baked > recordCapacity) {
            growRecords(baked);
        }

        while (published < baked && fitAtlas(published)) {
            BakedModel model = bakery.model(published);
            if (variantEntries + model.variantCount() > variants.capacity()) {
                growVariants(variantEntries + model.variantCount());
            }

            atlas.upload(published, model);
            records.write(published, model, variantEntries);
            if (model.variantCount() > 0) {
                variants.write(variantEntries, model.variants());
                variantEntries += model.variantCount();
            }

            published++;
        }
    }

    @Override
    public void close() {
        variants.close();
        records.close();
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

        int start = 0;
        for (int modelId = 0; modelId < published; modelId++) {
            BakedModel model = bakery.model(modelId);
            records.write(modelId, model, start);
            start += model.variantCount();
        }
    }

    private void growVariants(int wanted) {
        int grown = variants.capacity();
        while (grown < wanted) {
            grown *= GROWTH;
        }

        variants.close();
        variants = ModelVariants.create(grown);

        int start = 0;
        for (int modelId = 0; modelId < published; modelId++) {
            BakedModel model = bakery.model(modelId);
            if (model.variantCount() > 0) {
                variants.write(start, model.variants());
                start += model.variantCount();
            }
        }
    }
}
