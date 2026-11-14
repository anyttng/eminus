package com.eminus.client.model;

import java.util.Arrays;

import com.eminus.Eminus;
import com.eminus.gpu.Gpu;
import com.eminus.model.BakedModel;
import com.eminus.model.ModelSource;
import com.eminus.model.port.MipBlend;

import org.jspecify.annotations.Nullable;

public final class ModelPublisher implements AutoCloseable {
    public static final int START_ATLAS_CELLS_PER_SIDE = 4;
    public static final int START_RECORDS = 1024;
    public static final int START_VARIANTS = 256;

    private static final int GROWTH = 2;
    private static final long FRAME_BUDGET_NANOS = 500_000L;
    private static final long NO_BUDGET = Long.MAX_VALUE;

    private final Gpu gpu;
    private final ModelSource bakery;
    private final MipBlend blend;
    private ModelAtlas atlas;
    private ModelRecords records;
    private ModelVariants variants;
    private @Nullable ModelAtlas nextAtlas;
    private @Nullable ModelRecords nextRecords;
    private @Nullable ModelVariants nextVariants;
    private int[] variantStarts = new int[START_RECORDS];
    private int published;
    private int refilled;
    private int variantEntries;
    private int growths;
    private int reuploaded;
    private boolean atlasFull;

    private ModelPublisher(Gpu gpu, ModelSource bakery, MipBlend blend, ModelAtlas atlas, ModelRecords records,
            ModelVariants variants) {
        this.gpu = gpu;
        this.bakery = bakery;
        this.blend = blend;
        this.atlas = atlas;
        this.records = records;
        this.variants = variants;
    }

    public static ModelPublisher start(Gpu gpu, ModelSource bakery, int atlasFirstMip, MipBlend blend) {
        gpu.assertRenderThread();
        return new ModelPublisher(gpu, bakery, blend,
                ModelAtlas.create(gpu, START_ATLAS_CELLS_PER_SIDE, atlasFirstMip, blend),
                ModelRecords.create(gpu, START_RECORDS), ModelVariants.create(gpu, START_VARIANTS));
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

    public int baked() {
        return bakery.modelCount();
    }

    public boolean covers(int models) {
        return published >= models || atlasFull;
    }

    public int growths() {
        return growths;
    }

    public int reuploaded() {
        return reuploaded;
    }

    public void publish() {
        run(FRAME_BUDGET_NANOS);
    }

    public void publishAll() {
        run(NO_BUDGET);
    }

    private void run(long budgetNanos) {
        gpu.assertRenderThread();
        long started = System.nanoTime();
        while (step()) {
            if (System.nanoTime() - started >= budgetNanos) {
                break;
            }
        }
    }

    private boolean step() {
        if (regrowing()) {
            if (refilled < published) {
                refill(refilled);
                refilled++;
            } else {
                swap();
            }

            return true;
        }

        if (atlasFull || published >= bakery.modelCount()) {
            return false;
        }

        BakedModel model = bakery.model(published);
        if (makeRoom(model)) {
            return !atlasFull;
        }

        write(model);
        published++;
        return true;
    }

    private boolean regrowing() {
        return nextAtlas != null || nextRecords != null || nextVariants != null;
    }

    private boolean makeRoom(BakedModel model) {
        int baked = bakery.modelCount();
        if (!atlas.fits(published)) {
            ModelAtlas grown = atlas.grown(baked);
            if (grown == null) {
                Eminus.LOGGER.error("The model atlas is full at {} models; the rest stay unpublished", published);
                atlasFull = true;
                return true;
            }

            nextAtlas = grown;
        }

        if (published >= records.capacity()) {
            nextRecords = ModelRecords.create(gpu, grownCapacity(records.capacity(), baked));
        }

        int wanted = variantEntries + model.variantCount();
        if (wanted > variants.capacity()) {
            nextVariants = ModelVariants.create(gpu, grownCapacity(variants.capacity(), wanted));
        }

        refilled = 0;
        return regrowing();
    }

    private void write(BakedModel model) {
        atlas.upload(published, model);
        variantStarts[published] = variantEntries;
        records.write(published, model, variantEntries);
        if (model.variantCount() > 0) {
            variants.write(variantEntries, model.variants());
            variantEntries += model.variantCount();
        }
    }

    private void refill(int modelId) {
        BakedModel model = bakery.model(modelId);
        ModelAtlas grownAtlas = nextAtlas;
        if (grownAtlas != null) {
            grownAtlas.upload(modelId, model);
            reuploaded++;
        }

        ModelRecords grownRecords = nextRecords;
        if (grownRecords != null) {
            grownRecords.write(modelId, model, variantStarts[modelId]);
        }

        ModelVariants grownVariants = nextVariants;
        if (grownVariants != null && model.variantCount() > 0) {
            grownVariants.write(variantStarts[modelId], model.variants());
        }
    }

    private void swap() {
        ModelAtlas grownAtlas = nextAtlas;
        if (grownAtlas != null) {
            atlas.close();
            atlas = grownAtlas;
            nextAtlas = null;
            growths++;
        }

        ModelRecords grownRecords = nextRecords;
        if (grownRecords != null) {
            records.close();
            records = grownRecords;
            nextRecords = null;
            variantStarts = Arrays.copyOf(variantStarts, grownRecords.capacity());
        }

        ModelVariants grownVariants = nextVariants;
        if (grownVariants != null) {
            variants.close();
            variants = grownVariants;
            nextVariants = null;
        }
    }

    public void retexture(int atlasFirstMip) {
        gpu.assertRenderThread();
        dropNext();
        atlas.close();
        atlas = ModelAtlas.create(gpu, START_ATLAS_CELLS_PER_SIDE, atlasFirstMip, blend);
        atlasFull = false;
        published = 0;
        variantEntries = 0;
        publishAll();
    }

    @Override
    public void close() {
        dropNext();
        variants.close();
        records.close();
        atlas.close();
    }

    private void dropNext() {
        ModelAtlas grownAtlas = nextAtlas;
        if (grownAtlas != null) {
            grownAtlas.close();
            nextAtlas = null;
        }

        ModelRecords grownRecords = nextRecords;
        if (grownRecords != null) {
            grownRecords.close();
            nextRecords = null;
        }

        ModelVariants grownVariants = nextVariants;
        if (grownVariants != null) {
            grownVariants.close();
            nextVariants = null;
        }
    }

    private static int grownCapacity(int capacity, int wanted) {
        int grown = capacity;
        while (grown < wanted) {
            grown *= GROWTH;
        }

        return grown;
    }
}
