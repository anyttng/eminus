package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import com.eminus.InjectionTargets;
import com.eminus.client.frame.GameFrames;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.renderer.LevelRenderer;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    private static final String PRIORITIZE_CHUNK_UPDATES_OPTION =
            "Lnet/minecraft/client/Options;prioritizeChunkUpdates()Lnet/minecraft/client/OptionInstance;";

    @ModifyExpressionValue(method = "compileSections", at = @At(value = "INVOKE", target = InjectionTargets.OPTION_GET),
            slice = @Slice(from = @At(value = "INVOKE", target = InjectionTargets.FADE_IN_OPTION),
                    to = @At(value = "INVOKE", target = PRIORITIZE_CHUNK_UPDATES_OPTION)))
    private Object eminus$cancelSectionFadeIn(Object option) {
        return GameFrames.sectionFadeInSeconds((Double) option);
    }
}
