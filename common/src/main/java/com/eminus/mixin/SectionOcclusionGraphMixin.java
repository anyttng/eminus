package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.frame.GameFrames;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.world.phys.Vec3;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin {
    @Inject(method = "waitAndReset", at = @At("HEAD"))
    private void eminus$resetViewOrigin(ViewArea viewArea, CallbackInfo callback) {
        GameFrames.resetViewOrigin();
    }

    @Inject(method = "scheduleFullUpdate", at = @At("HEAD"))
    private void eminus$scheduleViewOrigin(boolean smartCull, Camera camera, Vec3 cameraPosition,
            CallbackInfo callback) {
        GameFrames.scheduleViewOrigin(cameraPosition);
    }

    @ModifyReturnValue(method = "consumeFrustumUpdate", at = @At("RETURN"))
    private boolean eminus$applyViewOrigin(boolean graphReplaced) {
        if (graphReplaced) {
            GameFrames.applyViewOrigin();
        }
        return graphReplaced;
    }
}
