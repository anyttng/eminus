package com.eminus.compat.sodium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.compat.sodium.SodiumDrawnSections;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.lists.SectionCollector")
public class SectionCollectorMixin {
    private static final String VISIT_WITH_FLAGS =
            "visit(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;I)V";

    @Inject(method = VISIT_WITH_FLAGS, at = @At("HEAD"))
    private void eminus$recordVisit(RenderSection section, int flags, CallbackInfo callback) {
        SodiumDrawnSections.visited(section.getChunkX(), section.getChunkY(), section.getChunkZ(), section.isBuilt());
    }
}
