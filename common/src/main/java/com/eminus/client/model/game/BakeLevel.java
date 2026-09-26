package com.eminus.client.model.game;

import com.eminus.model.port.TintBiome;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import org.jspecify.annotations.Nullable;

public record BakeLevel(Biome biome, String name, boolean positional) implements BlockAndTintGetter, TintBiome {
    private static final float UNSHADED = 1.0F;
    private static final float DOWN_SHADE = 0.5F;
    private static final float NORTH_SOUTH_SHADE = 0.8F;
    private static final float WEST_EAST_SHADE = 0.6F;
    private static final LevelLightEngine NO_LIGHT = new LevelLightEngine(new LightChunkGetter() {
        @Override
        public @Nullable LightChunk getChunkForLighting(int chunkX, int chunkZ) {
            return null;
        }

        @Override
        public BlockGetter getLevel() {
            return EmptyBlockGetter.INSTANCE;
        }
    }, false, false);

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver color) {
        return color.getColor(biome, pos.getX(), pos.getZ());
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) {
            return UNSHADED;
        }

        return switch (direction) {
            case DOWN -> DOWN_SHADE;
            case UP -> UNSHADED;
            case NORTH, SOUTH -> NORTH_SOUTH_SHADE;
            case WEST, EAST -> WEST_EAST_SHADE;
        };
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return NO_LIGHT;
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
