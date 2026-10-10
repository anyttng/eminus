package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;

import net.irisshaders.iris.shadows.ShadowRenderer;

@Mixin(ShadowRenderer.class)
public class ShadowRendererMixin {
    private static final String COPY_PRE_TRANSLUCENT_DEPTH = "Lnet/irisshaders/iris/shadows/ShadowRenderer;"
            + "copyPreTranslucentDepth(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;)V";

    @Shadow
    @Final
    private boolean shouldRenderTranslucent;

    @Inject(method = "renderShadows", at = @At(value = "INVOKE", target = COPY_PRE_TRANSLUCENT_DEPTH,
            shift = At.Shift.AFTER))
    private void eminus$drawTranslucentFarLayer(CallbackInfo callback) {
        if (shouldRenderTranslucent) {
            ClientSession.drawTranslucentFarLayerInShadowPass();
        }
    }
}
