package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.eminus.VanillaBootstrap;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelBakeryTest {
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int SECONDS = 5;

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
        ModelBakery bakery = ModelBakery.start(state -> BakedModel.solid(WHITE));

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
        ModelBakery bakery = ModelBakery.start(state -> BakedModel.solid(WHITE));

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
            return BakedModel.solid(WHITE);
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

            return BakedModel.solid(WHITE);
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
