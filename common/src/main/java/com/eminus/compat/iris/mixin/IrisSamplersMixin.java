package com.eminus.compat.iris.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.compat.iris.IrisFarState;
import com.eminus.compat.iris.PackContract;

import com.google.common.collect.ImmutableSet;

import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.targets.RenderTargets;

@Mixin(IrisSamplers.class)
public class IrisSamplersMixin {
    private static final Supplier<GlSampler> TEXTURE_SAMPLER = null;

    @Inject(method = "addRenderTargetSamplers", at = @At("RETURN"))
    private static void eminus$addFarDepth(SamplerHolder samplers, Supplier<ImmutableSet<Integer>> flipped,
            RenderTargets targets, boolean fullscreenPass, WorldRenderingPipeline pipeline, CallbackInfo callback) {
        samplers.addDynamicSampler(TextureType.TEXTURE_2D, IrisFarState::depth, TEXTURE_SAMPLER,
                PackContract.DEPTH_SAMPLER);
        samplers.addDynamicSampler(TextureType.TEXTURE_2D, IrisFarState::opaqueDepth, TEXTURE_SAMPLER,
                PackContract.OPAQUE_DEPTH_SAMPLER);
    }
}
