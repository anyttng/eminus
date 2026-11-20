package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import com.eminus.VanillaBootstrap;
import com.eminus.model.port.TintSource;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BiomeColorsTest {
    private static final int FIRST_BIOME = 0;
    private static final int SECOND_BIOME = 1;
    private static final int GRASS_RESOLVER = 0;
    private static final int FOLIAGE_RESOLVER = 1;
    private static final int BIOME_SHIFT = 8;
    private static final int SPRUCE = 0xFF61_9961;
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int RGB_MASK = 0x00FF_FFFF;
    private static final int UNPOWERED = 0;
    private static final int FULL_POWER = 15;
    private static final int SAMPLE_COLUMN = 0;
    private static final int AWAY_X = 7;

    private static BlockState stone;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        stone = Blocks.STONE.defaultBlockState();
    }

    private final BiomeColors colors = new BiomeColors(
            List.of(new FakeBiome(FIRST_BIOME), new FakeBiome(SECOND_BIOME)));

    @Test
    void aSourceThatVariesByBiomeResolvesToItsRowAndHoldsOneColorPerBiome() {
        TintSource grass = varying(GRASS_RESOLVER);
        colors.assign(List.of(colors.sample(grass, stone)));

        assertEquals(Tint.row(0), colors.resolve(grass, stone));
        assertEquals(BiomeColors.NO_COLOR, colors.constant(0));
        assertEquals(color(FIRST_BIOME, GRASS_RESOLVER), colors.color(0, FakeBiome.name(FIRST_BIOME)));
        assertEquals(color(SECOND_BIOME, GRASS_RESOLVER), colors.color(0, FakeBiome.name(SECOND_BIOME)));
        assertEquals(BiomeColors.NO_COLOR, colors.color(0, "minecraft:nether_wastes"));
    }

    @Test
    void twoInstancesWithTheSameColorsShareOneRow() {
        TintSource grass = varying(GRASS_RESOLVER);
        TintSource grassBlock = varying(GRASS_RESOLVER);
        colors.assign(List.of(colors.sample(grass, stone)));

        assertEquals(colors.sample(grass, stone), colors.sample(grassBlock, stone));
        assertEquals(Tint.row(0), colors.resolve(grassBlock, stone));
    }

    @Test
    void aSourceTheSameInEveryBiomeResolvesToItsRowAndAnswersItsConstant() {
        TintSource spruce = (state, biome, x, z) -> SPRUCE;
        colors.assign(List.of(colors.sample(spruce, stone)));

        assertEquals(Tint.row(0), colors.resolve(spruce, stone));
        assertEquals(SPRUCE & RGB_MASK, colors.constant(0));
    }

    @Test
    void aWhiteSourceDrawsUntintedEvenWithARow() {
        TintSource blank = (state, biome, x, z) -> WHITE;
        colors.assign(List.of(colors.sample(blank, stone)));

        Tint tint = colors.resolve(blank, stone);

        assertEquals(Tint.UNTINTED, tint);
        assertFalse(tint.hasRow());
    }

    @Test
    void aStateDependentSourceResolvesPerState() {
        TintSource power = (state, biome, x, z) -> state.getValue(RedstoneWireBlock.POWER);
        BlockState unpowered = Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedstoneWireBlock.POWER, UNPOWERED);
        BlockState powered = unpowered.setValue(RedstoneWireBlock.POWER, FULL_POWER);
        colors.assign(List.of(colors.sample(power, unpowered), colors.sample(power, powered)));

        assertNotEquals(colors.resolve(power, unpowered), colors.resolve(power, powered));
    }

    @Test
    void aVaryingSourceWithoutARowDrawsUntinted() {
        assertEquals(Tint.UNTINTED, colors.resolve(varying(FOLIAGE_RESOLVER), stone));
    }

    @Test
    void noSourceMeansNoTint() {
        assertNull(colors.resolve(null, stone));
    }

    @Test
    void aSourceThatThrowsResolvesUntinted() {
        TintSource throwing = (state, biome, x, z) -> {
            throw new IllegalStateException();
        };

        assertEquals(Tint.UNTINTED, colors.resolve(throwing, stone));
    }

    @Test
    void aSourceThatThrowsAwayFromTheSampleColumnAnswersWhiteThere() {
        BiomeColors positional = new BiomeColors(List.of(new FakeBiome(FIRST_BIOME, true)));
        TintSource source = throwingAwayFromTheSampleColumn();
        positional.assign(List.of(positional.sample(source, stone)));

        assertEquals(WHITE & RGB_MASK, positional.colorAt(0, FIRST_BIOME, AWAY_X, SAMPLE_COLUMN));
    }

    @Test
    void theFirstThrowOfASourceIsReportedOnceWithItsBlock() {
        List<Block> reported = new ArrayList<>();
        BiomeColors positional = new BiomeColors(List.of(new FakeBiome(FIRST_BIOME, true)),
                (block, failure) -> reported.add(block));
        TintSource source = throwingAwayFromTheSampleColumn();
        positional.assign(List.of(positional.sample(source, stone)));

        positional.colorAt(0, FIRST_BIOME, AWAY_X, SAMPLE_COLUMN);
        positional.colorAt(0, FIRST_BIOME, AWAY_X, AWAY_X);

        assertEquals(List.of(Blocks.STONE), reported);
    }

    private static TintSource throwingAwayFromTheSampleColumn() {
        return (state, biome, x, z) -> {
            if (x != SAMPLE_COLUMN) {
                throw new IllegalStateException();
            }

            return SPRUCE;
        };
    }

    private static TintSource varying(int resolver) {
        return (state, biome, x, z) -> color(((FakeBiome) biome).index(), resolver);
    }

    private static int color(int biome, int resolver) {
        return (biome + 1) << BIOME_SHIFT | resolver + 1;
    }
}
