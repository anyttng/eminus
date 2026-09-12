package com.eminus.compat.sodium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Minecraft;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager")
public class UniformBufferManagerMixin {
    private static final String OPTION_GET = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;";
    private static final String FADE_IN_OPTION =
            "Lnet/minecraft/client/Options;chunkSectionFadeInTime()Lnet/minecraft/client/OptionInstance;";
    private static final String TEXTURE_FILTERING_OPTION =
            "Lnet/minecraft/client/Options;textureFiltering()Lnet/minecraft/client/OptionInstance;";

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = OPTION_GET),
            slice = @Slice(from = @At(value = "INVOKE", target = FADE_IN_OPTION),
                    to = @At(value = "INVOKE", target = TEXTURE_FILTERING_OPTION)))
    private Object eminus$fadeInFromRenderState(Object option) {
        return Minecraft.getInstance().gameRenderer.gameRenderState().optionsRenderState.chunkSectionFadeInTime;
    }
}
