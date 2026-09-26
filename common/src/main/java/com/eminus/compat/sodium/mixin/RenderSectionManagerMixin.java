package com.eminus.compat.sodium.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.compat.sodium.SodiumDrawnSections;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager")
public class RenderSectionManagerMixin {
    private static final String RENDER_LISTS = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;"
            + "renderLists:Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/SortedRenderLists;";
    private static final String SEARCH_DISTANCE =
            "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;getSearchDistance()F";

    @Inject(method = "<init>", at = @At("RETURN"))
    private void eminus$resetDrawnSections(CallbackInfo callback) {
        SodiumDrawnSections.reset();
    }

    @ModifyExpressionValue(method = "createTerrainRenderList", at = @At(value = "INVOKE", target = SEARCH_DISTANCE))
    private float eminus$recordTraversalReach(float searchDistance, @Local(argsOnly = true) Viewport viewport) {
        CameraTransform camera = viewport.getTransform();
        SodiumDrawnSections.traversal(camera.intX, camera.intY, camera.intZ, camera.fracX, camera.fracY, camera.fracZ,
                searchDistance);
        return searchDistance;
    }

    @Inject(method = "onSectionRemoved", at = @At("HEAD"))
    private void eminus$forgetSection(int sectionX, int sectionY, int sectionZ, CallbackInfo callback) {
        SodiumDrawnSections.removed(sectionX, sectionY, sectionZ);
    }

    @Inject(method = "finalizeRenderLists",
            at = @At(value = "FIELD", target = RENDER_LISTS, opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void eminus$publishDrawnSections(CallbackInfo callback) {
        SodiumDrawnSections.publish();
    }
}
