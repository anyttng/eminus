package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import com.eminus.VanillaBootstrap;
import com.eminus.model.port.TintSource;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
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
        TintSource power = (state, biome, x, z) -> state.getValue(RedStoneWireBlock.POWER);
        BlockState unpowered = Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedStoneWireBlock.POWER, UNPOWERED);
        BlockState powered = unpowered.setValue(RedStoneWireBlock.POWER, FULL_POWER);
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

    private static TintSource varying(int resolver) {
        return (state, biome, x, z) -> color(((FakeBiome) biome).index(), resolver);
    }

    private static int color(int biome, int resolver) {
        return (biome + 1) << BIOME_SHIFT | resolver + 1;
    }
}
