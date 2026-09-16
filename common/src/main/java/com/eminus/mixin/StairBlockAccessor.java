package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(StairBlock.class)
public interface StairBlockAccessor {
    @Accessor("baseState")
    BlockState eminus$baseState();
}
