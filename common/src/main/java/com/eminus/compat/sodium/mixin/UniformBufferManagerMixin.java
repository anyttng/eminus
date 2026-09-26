package com.eminus.compat.sodium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import com.eminus.client.frame.GameFrames;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager")
public class UniformBufferManagerMixin {
    private static final String OPTION_GET = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;";
    private static final String FADE_IN_OPTION =
            "Lnet/minecraft/client/Options;chunkSectionFadeInTime()Lnet/minecraft/client/OptionInstance;";
    private static final String TEXTURE_FILTERING_OPTION =
            "Lnet/minecraft/client/Options;textureFiltering()Lnet/minecraft/client/OptionInstance;";
    private static final String ENVIRONMENTAL_FOG_START =
            "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;environmentalStart()F";
    private static final String ENVIRONMENTAL_FOG_END =
            "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;environmentalEnd()F";
    private static final String RENDER_FOG_START = "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;renderStart()F";
    private static final String RENDER_FOG_END = "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;renderEnd()F";

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = OPTION_GET),
            slice = @Slice(from = @At(value = "INVOKE", target = FADE_IN_OPTION),
                    to = @At(value = "INVOKE", target = TEXTURE_FILTERING_OPTION)))
    private Object eminus$fadeInFromRenderState(Object option) {
        return GameFrames.sectionFadeInSeconds();
    }

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = ENVIRONMENTAL_FOG_START))
    private float eminus$environmentalFogStartFromRenderState(float start) {
        return GameFrames.fog().environmentalStart();
    }

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = ENVIRONMENTAL_FOG_END))
    private float eminus$environmentalFogEndFromRenderState(float end) {
        return GameFrames.fog().environmentalEnd();
    }

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = RENDER_FOG_START))
    private float eminus$renderFogStartFromRenderState(float start) {
        return GameFrames.fog().renderDistanceStart();
    }

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = RENDER_FOG_END))
    private float eminus$renderFogEndFromRenderState(float end) {
        return GameFrames.fog().renderDistanceEnd();
    }
}
