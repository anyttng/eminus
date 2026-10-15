package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;
import com.eminus.compat.iris.PackSlot;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;

import net.minecraft.client.renderer.LevelRenderer;

@Mixin(LevelRenderer.class)
public class ClassicTransparencyMixin {
    private static final String MAIN_PASS = "lambda$addMainPass$0";
    private static final String RENDER_GROUP =
            "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup("
                    + "Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;"
                    + "Lcom/mojang/blaze3d/textures/GpuSampler;)V";
    private static final int TRANSLUCENT_TERRAIN = 1;

    @Inject(method = MAIN_PASS,
            at = @At(value = "INVOKE", target = RENDER_GROUP, ordinal = TRANSLUCENT_TERRAIN))
    private void eminus$drawTranslucentFarLayerBeforeNearTerrain(CallbackInfo callback) {
        if (Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline) {
            ClientSession.drawFarLayerInShaderPack(pipeline, PackSlot.BEFORE_NEAR_TRANSLUCENT);
        }
    }
}
