package com.eminus.model.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.eminus.Eminus;
import com.eminus.model.BakedModel;
import com.eminus.model.BiomeColours;
import com.eminus.model.ModelBakery;
import com.eminus.model.ModelMetadata;
import com.eminus.model.ModelSheet;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class ModelDump {
    public static final String FILE_NAME = "eminus-models.png";
    public static final int START_CELLS = 4;

    private static final String PROBE = "[eminus-bake]";
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

    public static Component sample() {
        return run(SAMPLE.stream().map(Block::defaultBlockState).toList(), Integer.MAX_VALUE, SAMPLE.size());
    }

    public static Component upTo(int models) {
        return run(everyState(), models, NO_DETAIL);
    }

    private static Component run(List<BlockState> states, int models, int detail) {
        Minecraft client = Minecraft.getInstance();
        Path file = client.gameDirectory.toPath().resolve(FILE_NAME);
        Eminus.LOGGER.info("{} dump states={} models={} file={}", PROBE, states.size(), models, file);

        ClientBakery baking = ClientBakery.start(client);
        ModelBakery bakery = baking.bakery();
        BiomeColours colours = baking.colours();

        try {
            bake(bakery, states, models);
            report(bakery, colours, detail);
            upload(bakery, colours);
            return sheet(bakery, file);
        } finally {
            baking.stop();
        }
    }

    private static void bake(ModelBakery bakery, List<BlockState> states, int models) {
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
                    Eminus.LOGGER.error("{} stalled state={}", PROBE, state);
                    return;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void upload(ModelBakery bakery, BiomeColours colours) {
        int count = bakery.modelCount();
        int growths = 0;
        int reuploaded = 0;

        try (ModelAtlas atlas = ModelAtlas.create(START_CELLS);
                ModelRecords records = ModelRecords.create(Math.max(count, 1))) {
            int from = atlas.cellsPerSide();

            for (int modelId = 0; modelId < count; modelId++) {
                while (!atlas.fits(modelId)) {
                    int moved = atlas.grow(bakery);
                    if (moved == 0) {
                        Eminus.LOGGER.error("{} atlas-full models={} uploaded={}", PROBE, count, modelId);
                        return;
                    }

                    growths++;
                    reuploaded += moved;
                }

                BakedModel model = bakery.model(modelId);
                atlas.upload(modelId, model);
                records.write(modelId, model, colours.row(model.tint()));
            }

            Eminus.LOGGER.info("{} atlas models={} growths={} reuploaded={} cells-from={} cells-to={} side={}",
                    PROBE, count, growths, reuploaded, from, atlas.cellsPerSide(), atlas.side());
        }
    }

    private static Component sheet(ModelBakery bakery, Path file) {
        List<BakedModel> models = new ArrayList<>();
        for (int modelId = 0; modelId < bakery.modelCount(); modelId++) {
            models.add(bakery.model(modelId));
        }

        try {
            ModelSheet.write(models, file);
        } catch (IOException failure) {
            Eminus.LOGGER.error("{} failed file={}", PROBE, file, failure);
            return Component.literal(failure.toString());
        }

        return Component.literal(file.toString());
    }

    private static void report(ModelBakery bakery, BiomeColours colours, int detail) {
        for (int modelId = 0; modelId < Math.min(bakery.modelCount(), detail); modelId++) {
            BakedModel model = bakery.model(modelId);
            Eminus.LOGGER.info("{} model id={} present={} occluding={} occludable={} tinted={} inset-up={}",
                    PROBE,
                    modelId,
                    ModelMetadata.present(model.metadata()),
                    ModelMetadata.occluding(model.metadata()),
                    ModelMetadata.occludable(model.metadata()),
                    model.tint() != null,
                    model.insets()[Direction.UP.ordinal()]);
        }

        Eminus.LOGGER.info("{} done models={} biomes={} tints={}",
                PROBE, bakery.modelCount(), colours.biomeCount(), colours.tintCount());
    }

    private static List<BlockState> everyState() {
        List<BlockState> states = new ArrayList<>();
        BuiltInRegistries.BLOCK.forEach(block -> states.addAll(block.getStateDefinition().getPossibleStates()));
        return states;
    }

    private ModelDump() {
    }
}
