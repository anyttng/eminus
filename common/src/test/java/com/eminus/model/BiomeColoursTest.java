package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import com.eminus.VanillaBootstrap;
import com.eminus.model.port.TintSource;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BiomeColoursTest {
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

    private final BiomeColours colours = new BiomeColours(
            List.of(new FakeBiome(FIRST_BIOME), new FakeBiome(SECOND_BIOME)));

    @Test
    void aSourceThatVariesByBiomeResolvesToItsRowAndHoldsOneColourPerBiome() {
        TintSource grass = varying(GRASS_RESOLVER);
        colours.assign(List.of(colours.sample(grass, stone)));

        assertEquals(Tint.row(0), colours.resolve(grass, stone));
        assertEquals(BiomeColours.NO_COLOUR, colours.constant(0));
        assertEquals(colour(FIRST_BIOME, GRASS_RESOLVER), colours.colour(0, FakeBiome.name(FIRST_BIOME)));
        assertEquals(colour(SECOND_BIOME, GRASS_RESOLVER), colours.colour(0, FakeBiome.name(SECOND_BIOME)));
        assertEquals(BiomeColours.NO_COLOUR, colours.colour(0, "minecraft:nether_wastes"));
    }

    @Test
    void twoInstancesWithTheSameColoursShareOneRow() {
        TintSource grass = varying(GRASS_RESOLVER);
        TintSource grassBlock = varying(GRASS_RESOLVER);
        colours.assign(List.of(colours.sample(grass, stone)));

        assertEquals(colours.sample(grass, stone), colours.sample(grassBlock, stone));
        assertEquals(Tint.row(0), colours.resolve(grassBlock, stone));
    }

    @Test
    void aSourceTheSameInEveryBiomeResolvesToItsRowAndAnswersItsConstant() {
        TintSource spruce = (state, biome, x, z) -> SPRUCE;
        colours.assign(List.of(colours.sample(spruce, stone)));

        assertEquals(Tint.row(0), colours.resolve(spruce, stone));
        assertEquals(SPRUCE & RGB_MASK, colours.constant(0));
    }

    @Test
    void aWhiteSourceDrawsUntintedEvenWithARow() {
        TintSource blank = (state, biome, x, z) -> WHITE;
        colours.assign(List.of(colours.sample(blank, stone)));

        Tint tint = colours.resolve(blank, stone);

        assertEquals(Tint.UNTINTED, tint);
        assertFalse(tint.hasRow());
    }

    @Test
    void aStateDependentSourceResolvesPerState() {
        TintSource power = (state, biome, x, z) -> state.getValue(RedstoneWireBlock.POWER);
        BlockState unpowered = Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedstoneWireBlock.POWER, UNPOWERED);
        BlockState powered = unpowered.setValue(RedstoneWireBlock.POWER, FULL_POWER);
        colours.assign(List.of(colours.sample(power, unpowered), colours.sample(power, powered)));

        assertNotEquals(colours.resolve(power, unpowered), colours.resolve(power, powered));
    }

    @Test
    void aVaryingSourceWithoutARowDrawsUntinted() {
        assertEquals(Tint.UNTINTED, colours.resolve(varying(FOLIAGE_RESOLVER), stone));
    }

    @Test
    void noSourceMeansNoTint() {
        assertNull(colours.resolve(null, stone));
    }

    private static TintSource varying(int resolver) {
        return (state, biome, x, z) -> colour(((FakeBiome) biome).index(), resolver);
    }

    private static int colour(int biome, int resolver) {
        return (biome + 1) << BIOME_SHIFT | resolver + 1;
    }
}
