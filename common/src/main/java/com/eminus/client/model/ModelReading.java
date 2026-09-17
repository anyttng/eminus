package com.eminus.client.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.eminus.model.BakedModel;
import com.eminus.model.BiomeColours;
import com.eminus.model.ModelBakery;
import com.eminus.model.ModelMetadata;
import com.eminus.model.ModelSheet;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class ModelReading {
    public static final String FILE_NAME = "eminus-models.png";
    public static final int MODELS = 0;
    public static final int BIOMES = 1;
    public static final int TINT_ROWS = 2;
    public static final int UPLOADED = 3;
    public static final int GROWTHS = 4;
    public static final int REUPLOADED = 5;
    public static final int CELLS_FROM = 6;
    public static final int CELLS_TO = 7;
    public static final int SIDE = 8;
    private static final int SUMMARY_WIDTH = 9;

    public static final int MODEL_ID = 0;
    public static final int PRESENT = 1;
    public static final int OCCLUDING = 2;
    public static final int OCCLUDABLE = 3;
    public static final int TINTED = 4;
    public static final int INSET_UP_BITS = 5;

    private static final int START_CELLS = 4;
    private static final int BAKE_TIMEOUT_SECONDS = 60;
    private static final int NO_DETAIL = 0;
    private static final List<Block> SAMPLE = List.of(
            Blocks.STONE,
            Blocks.GRASS_BLOCK,
            Blocks.GLASS,
            Blocks.WATER,
            Blocks.OAK_LEAVES,
            Blocks.STONE_SLAB,
            Blocks.OAK_STAIRS,
            Blocks.OAK_FENCE,
            Blocks.TORCH);

    public static List<long[]> sample() {
        return run(SAMPLE.stream().map(Block::defaultBlockState).toList(), Integer.MAX_VALUE, SAMPLE.size());
    }

    public static List<long[]> upTo(int models) {
        return run(everyState(), models, NO_DETAIL);
    }

    private static List<long[]> run(List<BlockState> states, int models, int detail) {
        Minecraft client = Minecraft.getInstance();
        ClientBakery baking = ClientBakery.start(client);
        ModelBakery bakery = baking.bakery();
        BiomeColours colours = baking.colours();

        try {
            bake(bakery, states, models);
            long[] summary = new long[SUMMARY_WIDTH];
            summary[MODELS] = bakery.modelCount();
            summary[BIOMES] = colours.biomeCount();
            summary[TINT_ROWS] = colours.rowCount();
            upload(bakery, summary);
            sheet(bakery, client.gameDirectory.toPath().resolve(FILE_NAME));

            List<long[]> rows = new ArrayList<>();
            rows.add(summary);
            rows.addAll(details(bakery, detail));
            return rows;
        } finally {
            baking.stop();
        }
    }

    static void bake(ModelBakery bakery, List<BlockState> states, int models) {
        for (BlockState state : states) {
            if (bakery.modelCount() >= models) {
                return;
            }

            CountDownLatch served = new CountDownLatch(1);
            if (bakery.request(state, served::countDown) != ModelBakery.MISSING) {
                continue;
            }

            try {
                if (!served.await(BAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The bake of " + state + " did not finish within "
                            + BAKE_TIMEOUT_SECONDS + " seconds.");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void upload(ModelBakery bakery, long[] summary) {
        int count = bakery.modelCount();

        try (ModelAtlas atlas = ModelAtlas.create(START_CELLS);
                ModelRecords records = ModelRecords.create(Math.max(count, 1))) {
            summary[CELLS_FROM] = atlas.cellsPerSide();
            int variantStart = 0;

            for (int modelId = 0; modelId < count; modelId++) {
                while (!atlas.fits(modelId)) {
                    int moved = atlas.grow(bakery);
                    if (moved == 0) {
                        break;
                    }

                    summary[GROWTHS]++;
                    summary[REUPLOADED] += moved;
                }

                if (!atlas.fits(modelId)) {
                    break;
                }

                BakedModel model = bakery.model(modelId);
                atlas.upload(modelId, model);
                records.write(modelId, model, variantStart);
                variantStart += model.variantCount();
                summary[UPLOADED]++;
            }

            summary[CELLS_TO] = atlas.cellsPerSide();
            summary[SIDE] = atlas.side();
        }
    }

    private static void sheet(ModelBakery bakery, Path file) {
        List<BakedModel> models = new ArrayList<>();
        for (int modelId = 0; modelId < bakery.modelCount(); modelId++) {
            models.add(bakery.model(modelId));
        }

        try {
            ModelSheet.write(models, file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static List<long[]> details(ModelBakery bakery, int detail) {
        List<long[]> rows = new ArrayList<>();
        for (int modelId = 0; modelId < Math.min(bakery.modelCount(), detail); modelId++) {
            BakedModel model = bakery.model(modelId);
            rows.add(new long[] {
                    modelId,
                    ModelMetadata.present(model.metadata()),
                    ModelMetadata.occluding(model.metadata()),
                    ModelMetadata.occludable(model.metadata()),
                    model.tintRow() != BiomeColours.NO_ROW ? 1 : 0,
                    Float.floatToIntBits(model.insets()[Direction.UP.ordinal()])});
        }

        return rows;
    }

    static List<BlockState> everyState() {
        List<BlockState> states = new ArrayList<>();
        BuiltInRegistries.BLOCK.forEach(block -> states.addAll(block.getStateDefinition().getPossibleStates()));
        return states;
    }

    private ModelReading() {
    }
}
