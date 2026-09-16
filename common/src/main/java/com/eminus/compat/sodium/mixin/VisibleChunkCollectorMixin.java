package com.eminus.compat.sodium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.compat.sodium.SodiumDrawnSections;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector")
public class VisibleChunkCollectorMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void eminus$beginTraversal(CallbackInfo callback) {
        SodiumDrawnSections.beginTraversal();
    }

    @Inject(method = "visit", at = @At("HEAD"))
    private void eminus$recordVisit(int x, int y, int z, CallbackInfo callback) {
        SodiumDrawnSections.visited(x, y, z);
    }
}
