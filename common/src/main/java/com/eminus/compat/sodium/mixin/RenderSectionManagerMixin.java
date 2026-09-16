package com.eminus.compat.sodium.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.compat.sodium.SodiumDrawnSections;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager")
public class RenderSectionManagerMixin {
    private static final String RENDER_LISTS = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;"
            + "renderLists:Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/SortedRenderLists;";

    @Inject(method = {"readRenderListFromTree", "renderOutOfGraph"},
            at = @At(value = "FIELD", target = RENDER_LISTS, opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void eminus$publishDrawnSections(CallbackInfo callback) {
        SodiumDrawnSections.publish();
    }
}
