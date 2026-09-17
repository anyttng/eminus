package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.util.random.WeightedList;

@Mixin(WeightedVariants.class)
public interface WeightedVariantsAccessor {
    @Accessor("list")
    WeightedList<BlockStateModel> eminus$list();
}
