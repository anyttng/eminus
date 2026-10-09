package com.eminus.compat.iris.mixin;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
    private static final String WORLD_DEPTH_SAMPLERS = "addWorldDepthSamplers(Lnet/irisshaders/iris/gl/sampler/"
            + "SamplerHolder;Lnet/irisshaders/iris/targets/RenderTargets;)V";
    private static final String COMPOSITE_SAMPLERS = "addCompositeSamplers(Lnet/irisshaders/iris/gl/sampler/"
            + "SamplerHolder;Lnet/irisshaders/iris/targets/RenderTargets;)V";
    private static final String ADD_DEPTH_SAMPLER = "Lnet/irisshaders/iris/gl/sampler/SamplerHolder;"
            + "addDynamicSampler(Ljava/util/function/IntSupplier;Lnet/irisshaders/iris/gl/sampler/GlSampler;"
            + "[Ljava/lang/String;)Z";
    private static final int NAMES = 2;

    @Inject(method = "addRenderTargetSamplers", at = @At("RETURN"))
    private static void eminus$addFarDepth(SamplerHolder samplers, Supplier<ImmutableSet<Integer>> flipped,
            RenderTargets targets, boolean fullscreenPass, WorldRenderingPipeline pipeline, CallbackInfo callback) {
        samplers.addDynamicSampler(TextureType.TEXTURE_2D, IrisFarState::depth, TEXTURE_SAMPLER,
                PackContract.DEPTH_SAMPLER, PackContract.VIEW_FAR_DEPTH);
        samplers.addDynamicSampler(TextureType.TEXTURE_2D, IrisFarState::opaqueDepth, TEXTURE_SAMPLER,
                PackContract.OPAQUE_DEPTH_SAMPLER, PackContract.VIEW_FAR_OPAQUE_DEPTH);
    }

    @ModifyArg(method = { WORLD_DEPTH_SAMPLERS, COMPOSITE_SAMPLERS },
            at = @At(value = "INVOKE", target = ADD_DEPTH_SAMPLER), index = NAMES)
    private static String[] eminus$nameNearDepthForViewPosition(String[] names) {
        List<String> named = Arrays.asList(names);
        if (named.contains(PackContract.NEAR_DEPTH)) {
            return eminus$with(names, PackContract.VIEW_NEAR_DEPTH);
        }
        if (named.contains(PackContract.NEAR_OPAQUE_DEPTH)) {
            return eminus$with(names, PackContract.VIEW_NEAR_OPAQUE_DEPTH);
        }
        return names;
    }

    @Unique
    private static String[] eminus$with(String[] names, String alias) {
        String[] extended = Arrays.copyOf(names, names.length + 1);
        extended[names.length] = alias;
        return extended;
    }
}
