package com.eminus.compat.sodium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import com.eminus.InjectionTargets;
import com.eminus.handoff.NearFieldOverride;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Minecraft;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager")
public class UniformBufferManagerMixin {
    private static final String TEXTURE_FILTERING_OPTION =
            "Lnet/minecraft/client/Options;textureFiltering()Lnet/minecraft/client/OptionInstance;";
    private static final String ENVIRONMENTAL_FOG_START =
            "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;environmentalStart()F";
    private static final String ENVIRONMENTAL_FOG_END =
            "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;environmentalEnd()F";
    private static final String RENDER_FOG_START = "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;renderStart()F";
    private static final String RENDER_FOG_END = "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;renderEnd()F";

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = InjectionTargets.OPTION_GET),
            slice = @Slice(from = @At(value = "INVOKE", target = InjectionTargets.FADE_IN_OPTION),
                    to = @At(value = "INVOKE", target = TEXTURE_FILTERING_OPTION)))
    private Object eminus$cancelSectionFadeIn(Object option) {
        return NearFieldOverride.fadeInTime((Double) option);
    }

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = ENVIRONMENTAL_FOG_START))
    private float eminus$environmentalFogStartFromRenderState(float start) {
        return Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.fogData
                .environmentalStart;
    }

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = ENVIRONMENTAL_FOG_END))
    private float eminus$environmentalFogEndFromRenderState(float end) {
        return Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.fogData
                .environmentalEnd;
    }

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = RENDER_FOG_START))
    private float eminus$renderFogStartFromRenderState(float start) {
        return Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.fogData
                .renderDistanceStart;
    }

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = RENDER_FOG_END))
    private float eminus$renderFogEndFromRenderState(float end) {
        return Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.fogData
                .renderDistanceEnd;
    }
}
