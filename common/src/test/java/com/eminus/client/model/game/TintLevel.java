package com.eminus.client.model.game;

import java.util.List;

import com.eminus.model.port.TintBiome;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import org.jspecify.annotations.Nullable;

record TintLevel(int biome) implements BlockAndTintGetter, TintBiome {
    private static final List<ColorResolver> RESOLVERS = List.of(BiomeColors.GRASS_COLOR_RESOLVER,
            BiomeColors.FOLIAGE_COLOR_RESOLVER, BiomeColors.WATER_COLOR_RESOLVER);
    private static final int BIOME_SHIFT = 8;
    private static final String PREFIX = "test:biome_";
    private static final float UNSHADED = 1.0F;

    static int colour(int biome, int resolver) {
        return (biome + 1) << BIOME_SHIFT | resolver + 1;
    }

    @Override
    public String name() {
        return PREFIX + biome;
    }

    @Override
    public boolean positional() {
        return false;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver color) {
        return colour(biome, RESOLVERS.indexOf(color));
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return UNSHADED;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
    }
}
