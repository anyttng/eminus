package com.eminus.client.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.eminus.VanillaBootstrap;
import com.eminus.model.BiomeColors;
import com.eminus.model.Tint;
import com.eminus.model.TintSweep;
import com.eminus.model.port.BlockModel;
import com.eminus.model.port.BlockModels;
import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.TintBiome;
import com.eminus.model.port.TintSource;
import com.eminus.model.port.Variant;
import com.eminus.model.port.VariantDraw;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import org.joml.Vector3fc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GameTintsTest {
    private static final int FIRST_BIOME = 0;
    private static final int SECOND_BIOME = 1;
    private static final int TWO_BIOMES = 2;
    private static final int THREE_BIOMES = 3;
    private static final int GRASS_RESOLVER = 0;
    private static final int VANILLA_ROWS = 30;
    private static final int SPRUCE_LEAVES = -10380959;
    private static final int FIRST_LAYER = 0;
    private static final int SECOND_LAYER = 1;
    private static final int SAMPLE_COLUMN = 0;
    private static final int RGB_MASK = 0x00FF_FFFF;
    private static final int WHITE = -1;
    private static final int UNPOWERED = 0;
    private static final int FULL_POWER = 15;

    private static BlockColors blockColors;
    private static BlockState stone;
    private static BlockState grassBlock;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        blockColors = BlockColors.createDefault();
        stone = Blocks.STONE.defaultBlockState();
        grassBlock = Blocks.GRASS_BLOCK.defaultBlockState();
    }

    @Test
    void aVanillaColorAnswersWithTheBiomeOfTheLevelItIsHanded() {
        TintSource grass = new GameTintSource(blockColors, FIRST_LAYER);

        for (int biome : new int[] {FIRST_BIOME, SECOND_BIOME}) {
            assertEquals(TintLevel.color(biome, GRASS_RESOLVER),
                    grass.color(grassBlock, new TintLevel(biome), SAMPLE_COLUMN, SAMPLE_COLUMN) & RGB_MASK);
        }
    }

    @Test
    void twoVanillaColorsWithTheSameValuesShareOneRow() {
        BiomeColors colors = colors(TWO_BIOMES);
        TintSource layer = new GameTintSource(blockColors, FIRST_LAYER);
        colors.assign(List.of(colors.sample(layer, grassBlock)));

        assertEquals(Tint.row(0), colors.resolve(layer, Blocks.SUGAR_CANE.defaultBlockState()));
    }

    @Test
    void aStateDependentVanillaColorResolvesPerState() {
        BiomeColors colors = colors(TWO_BIOMES);
        TintSource redstone = new GameTintSource(blockColors, FIRST_LAYER);
        BlockState unpowered = Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedStoneWireBlock.POWER, UNPOWERED);
        BlockState powered = unpowered.setValue(RedStoneWireBlock.POWER, FULL_POWER);
        colors.assign(List.of(colors.sample(redstone, unpowered), colors.sample(redstone, powered)));

        assertNotEquals(colors.resolve(redstone, unpowered), colors.resolve(redstone, powered));
    }

    @Test
    void vanillaColorsNeedARowPerBiomeSetAndPerConstant() {
        GameTints tints = new GameTints(blockColors, new LayerModels(FIRST_LAYER));
        TintSource water = (state, biome, blockX, blockZ) ->
                net.minecraft.client.renderer.BiomeColors.getAverageWaterColor((BlockAndTintGetter) biome,
                        BlockPos.ZERO);
        BiomeColors colors = colors(THREE_BIOMES);

        TintSweep.sweep(Block.BLOCK_STATE_REGISTRY, tints,
                fluid -> fluid.getType().isSame(Fluids.WATER) ? water : null, state -> state, colors);

        assertEquals(VANILLA_ROWS, colors.rowCount());
        assertTrue(colors.resolve(tints.source(grassBlock, FIRST_LAYER), grassBlock).hasRow());

        BlockState spruce = Blocks.SPRUCE_LEAVES.defaultBlockState();
        Tint spruceTint = colors.resolve(tints.source(spruce, FIRST_LAYER), spruce);
        assertTrue(spruceTint.hasRow());
        assertEquals(SPRUCE_LEAVES & RGB_MASK, colors.constant(spruceTint.row()));
    }

    @Test
    void theSourcesAreTheDistinctTintedLayersOfTheModel() {
        GameTints tints = new GameTints(blockColors,
                new LayerModels(SECOND_LAYER, ModelQuad.NO_TINT, SECOND_LAYER, FIRST_LAYER));

        assertEquals(List.of(new GameTintSource(blockColors, FIRST_LAYER),
                        new GameTintSource(blockColors, SECOND_LAYER)),
                tints.sources(grassBlock));
    }

    @Test
    void aStateWhoseModelTintsNoQuadHasNoSource() {
        GameTints tints = new GameTints(blockColors, new LayerModels(ModelQuad.NO_TINT));

        assertTrue(tints.sources(stone).isEmpty());
    }

    @Test
    void aLayerOfABlockWithoutAColorAnswersWhite() {
        GameTints tints = new GameTints(blockColors, new LayerModels());

        assertEquals(WHITE,
                tints.source(stone, FIRST_LAYER).color(stone, new TintLevel(FIRST_BIOME), SAMPLE_COLUMN,
                        SAMPLE_COLUMN));
    }

    private static BiomeColors colors(int count) {
        List<TintBiome> biomes = new ArrayList<>();
        for (int biome = 0; biome < count; biome++) {
            biomes.add(new TintLevel(biome));
        }

        return new BiomeColors(biomes);
    }

    @SuppressWarnings("ArrayRecordComponent")
    private record LayerModels(int... layers) implements BlockModels {
        @Override
        public BlockModel model(BlockState state) {
            return new BlockModel() {
                @Override
                public void quads(RandomSource random, List<ModelQuad> into) {
                    for (int layer : layers) {
                        into.add(new ModelQuad(new Vector3fc[ModelQuad.CORNERS], new float[ModelQuad.CORNERS * 2],
                                null, layer, false, 0, Direction.UP, Direction.UP, true));
                    }
                }

                @Override
                public List<Variant> variants() {
                    return List.of();
                }
            };
        }

        @Override
        public void pick(BlockState state, RandomSource random, List<Object> parts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void quads(List<Object> parts, List<ModelQuad> into) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean forceOpaque(BlockState state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VariantDraw variantDraw() {
            throw new UnsupportedOperationException();
        }
    }
}
