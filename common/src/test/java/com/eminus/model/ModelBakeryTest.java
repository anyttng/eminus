package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.eminus.VanillaBootstrap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelBakeryTest {
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int BLUE = 0xFF00_00FF;
    private static final int GREEN = 0xFF00_FF00;
    private static final int BOTH_MODELS = 2;
    private static final int SECONDS = 5;
    private static final int RACED_STATES = 2000;

    private static BlockState stone;
    private static BlockState dirt;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        stone = Blocks.STONE.defaultBlockState();
        dirt = Blocks.DIRT.defaultBlockState();
    }

    @Test
    void twoStatesThatBakeToTheSameResultShareOneModelId() throws InterruptedException {
        ModelBakery bakery = ModelBakery.start(state -> new BakedState(BakedModel.solid(WHITE), null));

        try {
            awaitBake(bakery, stone);
            awaitBake(bakery, dirt);

            assertEquals(bakery.modelId(stone), bakery.modelId(dirt));
            assertEquals(1, bakery.modelCount());
        } finally {
            bakery.stop();
        }
    }

    @Test
    void aRequestForAnUnbakedStateIsAnsweredOnceItIsServed() throws InterruptedException {
        ModelBakery bakery = ModelBakery.start(state -> new BakedState(BakedModel.solid(WHITE), null));

        try {
            CountDownLatch served = new CountDownLatch(1);

            assertEquals(ModelBakery.MISSING, bakery.request(stone, served::countDown));
            assertTrue(served.await(SECONDS, TimeUnit.SECONDS));
            assertNotEquals(ModelBakery.MISSING, bakery.modelId(stone));
        } finally {
            bakery.stop();
        }
    }

    @Test
    void everyRequesterOfOneStateIsAnsweredByTheSingleBake() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        ModelBakery bakery = ModelBakery.start(state -> {
            await(release);
            return new BakedState(BakedModel.solid(WHITE), null);
        });

        try {
            CountDownLatch served = new CountDownLatch(2);

            assertEquals(ModelBakery.MISSING, bakery.request(stone, served::countDown));
            assertEquals(ModelBakery.MISSING, bakery.request(stone, served::countDown));
            release.countDown();

            assertTrue(served.await(SECONDS, TimeUnit.SECONDS));
            assertEquals(1, bakery.modelCount());
        } finally {
            bakery.stop();
        }
    }

    @Test
    void aThrowingBakeYieldsThePlaceholderAndLeavesTheThreadServing() throws InterruptedException {
        ModelBakery bakery = ModelBakery.start(state -> {
            if (state == stone) {
                throw new IllegalStateException("no model for " + state);
            }

            return new BakedState(BakedModel.solid(WHITE), null);
        });

        try {
            awaitBake(bakery, stone);
            assertEquals(BakedModel.solid(ModelBakery.PLACEHOLDER_COLOUR), bakery.model(bakery.modelId(stone)));

            awaitBake(bakery, dirt);
            assertEquals(BakedModel.solid(WHITE), bakery.model(bakery.modelId(dirt)));
        } finally {
            bakery.stop();
        }
    }

    @Test
    void aStateWithNoFluidPublishesNoFluidModel() throws InterruptedException {
        ModelBakery bakery = ModelBakery.start(state -> new BakedState(BakedModel.solid(WHITE), null));

        try {
            awaitBake(bakery, stone);

            assertEquals(ModelBakery.NO_FLUID, bakery.fluidModelId(stone));
            assertEquals(1, bakery.modelCount());
        } finally {
            bakery.stop();
        }
    }

    @Test
    void aStateWithAFluidPublishesASecondModelId() throws InterruptedException {
        ModelBakery bakery = ModelBakery.start(
                state -> new BakedState(BakedModel.solid(WHITE), BakedModel.solid(BLUE)));

        try {
            awaitBake(bakery, stone);

            assertNotEquals(ModelBakery.MISSING, bakery.fluidModelId(stone));
            assertNotEquals(bakery.modelId(stone), bakery.fluidModelId(stone));
            assertEquals(BakedModel.solid(BLUE), bakery.model(bakery.fluidModelId(stone)));
            assertEquals(BOTH_MODELS, bakery.modelCount());
        } finally {
            bakery.stop();
        }
    }

    @Test
    void aSubmergedTwinAnswersForTheFluidModelAndEveryOtherModelAnswersItself() throws InterruptedException {
        ModelBakery bakery = ModelBakery.start(state -> state == stone
                ? new BakedState(BakedModel.solid(WHITE), BakedModel.solid(BLUE), BakedModel.solid(GREEN))
                : new BakedState(BakedModel.solid(BLUE), null, BakedModel.solid(GREEN)));

        try {
            awaitBake(bakery, stone);
            awaitBake(bakery, dirt);

            int twin = bakery.submergedModelId(bakery.fluidModelId(stone));
            assertEquals(BakedModel.solid(GREEN), bakery.model(twin));
            assertEquals(twin, bakery.submergedModelId(bakery.modelId(dirt)));
            assertEquals(bakery.modelId(stone), bakery.submergedModelId(bakery.modelId(stone)));
        } finally {
            bakery.stop();
        }
    }

    @Test
    void twoStatesWithTheSameBlockAndFluidShareBothIds() throws InterruptedException {
        ModelBakery bakery = ModelBakery.start(
                state -> new BakedState(BakedModel.solid(WHITE), BakedModel.solid(BLUE)));

        try {
            awaitBake(bakery, stone);
            awaitBake(bakery, dirt);

            assertEquals(bakery.modelId(stone), bakery.modelId(dirt));
            assertEquals(bakery.fluidModelId(stone), bakery.fluidModelId(dirt));
            assertEquals(BOTH_MODELS, bakery.modelCount());
        } finally {
            bakery.stop();
        }
    }

    @Test
    void anIdIsVisibleOnlyOnceTheModelCountCoversIt() throws InterruptedException {
        AtomicInteger colour = new AtomicInteger();
        ModelBakery bakery = ModelBakery.start(state -> new BakedState(
                BakedModel.solid(colour.incrementAndGet()), BakedModel.solid(colour.incrementAndGet())));
        List<BlockState> states = BuiltInRegistries.BLOCK.stream()
                .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
                .limit(RACED_STATES)
                .toList();

        try {
            CountDownLatch served = new CountDownLatch(states.size());
            for (BlockState state : states) {
                bakery.request(state, served::countDown);
            }

            while (served.getCount() > 0) {
                for (BlockState state : states) {
                    int block = bakery.modelId(state);
                    int fluid = bakery.fluidModelId(state);
                    int count = bakery.modelCount();

                    assertTrue(block < count, "block model " + block + " visible at count " + count);
                    assertTrue(fluid < count, "fluid model " + fluid + " visible at count " + count);
                }
            }
        } finally {
            bakery.stop();
        }
    }

    private static void awaitBake(ModelBakery bakery, BlockState state) throws InterruptedException {
        CountDownLatch served = new CountDownLatch(1);
        if (bakery.request(state, served::countDown) == ModelBakery.MISSING) {
            assertTrue(served.await(SECONDS, TimeUnit.SECONDS), "the bake of " + state + " never landed");
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
