package com.eminus.mixin;

import java.util.Optional;
import java.util.OptionalDouble;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    private static final String MAIN_PASS = "lambda$addMainPass$0";
    private static final String CLASSIC_TRANSPARENCY =
            "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency("
                    + "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;"
                    + "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"
                    + "Lcom/mojang/renderpearl/api/commands/RenderPass;)V";
    private static final String OIT = "Lnet/minecraft/client/renderer/LevelRenderer;executeOit("
            + "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;"
            + "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V";
    private static final String OUTLINE = "Lnet/minecraft/client/renderer/LevelRenderer;executeOutline("
            + "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V";
    private static final String DEFERRED = "classicTransparencyDeferred";
    private static final String TRANSLUCENT_PASS_LABEL = "eminus-translucent-after-far";

    @Shadow
    private void executeClassicTransparency(ChunkSectionsToRender chunkSectionsToRender,
            FeatureRenderDispatcher.PreparedFrame featureFrame, RenderPass renderPass) {
    }

    // The far layer needs its own passes between solid and translucent terrain, which 26.3 draws in one open pass.
    @WrapOperation(method = MAIN_PASS, at = @At(value = "INVOKE", target = CLASSIC_TRANSPARENCY))
    private void eminus$deferClassicTransparency(LevelRenderer renderer, ChunkSectionsToRender chunkSectionsToRender,
            FeatureRenderDispatcher.PreparedFrame featureFrame, RenderPass renderPass, Operation<Void> original,
            @Share(DEFERRED) LocalBooleanRef deferred) {
        deferred.set(true);
    }

    @Inject(method = MAIN_PASS, at = @At(value = "INVOKE", target = OIT))
    private void eminus$drawFarLayerBeforeOit(CallbackInfo callback) {
        ClientSession.drawFarLayer();
    }

    @Inject(method = MAIN_PASS, at = @At(value = "INVOKE", target = OUTLINE))
    private void eminus$drawFarLayerBeforeClassicTransparency(CallbackInfo callback,
            @Local ChunkSectionsToRender chunkSectionsToRender, @Local FeatureRenderDispatcher.PreparedFrame featureFrame,
            @Local RenderTarget mainTarget, @Share(DEFERRED) LocalBooleanRef deferred) {
        if (!deferred.get()) {
            return;
        }

        ClientSession.drawFarLayer();
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> TRANSLUCENT_PASS_LABEL, mainTarget.getColorTextureView(), Optional.empty(),
                mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(renderPass);
            executeClassicTransparency(chunkSectionsToRender, featureFrame, renderPass);
        }
    }
}
