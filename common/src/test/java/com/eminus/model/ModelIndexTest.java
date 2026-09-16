package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelIndexTest {
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int BLUE = 0xFF00_00FF;
    private static final int SECONDS = 5;

    private static BlockState stone;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        stone = Blocks.STONE.defaultBlockState();
    }

    @Test
    void aStateIdAnswersWithItsModelIdOnceTheBakeLands() throws InterruptedException {
        StateTable states = new StateTable(new Dictionary<>((id, value) -> { }));
        ModelBakery bakery = ModelBakery.start(state -> new BakedState(BakedModel.solid(WHITE), null));
        ModelIndex index = new ModelIndex(states, bakery);

        try {
            int stateId = states.idOf(stone);
            CountDownLatch served = new CountDownLatch(1);

            assertEquals(ModelBakery.MISSING, index.modelId(stateId, served::countDown));
            assertTrue(served.await(SECONDS, TimeUnit.SECONDS));
            assertEquals(bakery.modelId(stone), index.modelId(stateId, () -> { }));
        } finally {
            bakery.stop();
        }
    }

    @Test
    void aStateIdAnswersWithItsFluidModelIdOnceTheBakeLands() throws InterruptedException {
        StateTable states = new StateTable(new Dictionary<>((id, value) -> { }));
        ModelBakery bakery = ModelBakery.start(
                state -> new BakedState(BakedModel.solid(WHITE), BakedModel.solid(BLUE)));
        ModelIndex index = new ModelIndex(states, bakery);

        try {
            int stateId = states.idOf(stone);
            CountDownLatch served = new CountDownLatch(1);

            assertEquals(ModelBakery.MISSING, index.fluidModelId(stateId, served::countDown));
            assertTrue(served.await(SECONDS, TimeUnit.SECONDS));
            assertEquals(bakery.fluidModelId(stone), index.fluidModelId(stateId, () -> { }));
        } finally {
            bakery.stop();
        }
    }

    @Test
    void aStateIdWithoutAFluidAnswersNoFluid() throws InterruptedException {
        StateTable states = new StateTable(new Dictionary<>((id, value) -> { }));
        ModelBakery bakery = ModelBakery.start(state -> new BakedState(BakedModel.solid(WHITE), null));
        ModelIndex index = new ModelIndex(states, bakery);

        try {
            int stateId = states.idOf(stone);
            CountDownLatch served = new CountDownLatch(1);

            assertEquals(ModelBakery.MISSING, index.modelId(stateId, served::countDown));
            assertTrue(served.await(SECONDS, TimeUnit.SECONDS));
            assertEquals(ModelBakery.NO_FLUID, index.fluidModelId(stateId, () -> { }));
        } finally {
            bakery.stop();
        }
    }
}
