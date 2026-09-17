package com.eminus.compat.sodium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.eminus.handoff.NearFieldOverride;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager")
public class RenderRegionManagerMixin {
    private static final String WRITE_MESH_TIMES =
            "Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;writeMeshTimes(III)V";
    private static final String UPLOAD_REGION_RESULTS = "uploadResults("
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;"
            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;)V";
    private static final int BUILT_TIME_ARG = 2;
    private static final int NO_FADE = -1;

    // A zero fade period makes Sodium's shader paint a timed section's first frame in fog colour.
    @ModifyArg(method = UPLOAD_REGION_RESULTS, at = @At(value = "INVOKE", target = WRITE_MESH_TIMES),
            index = BUILT_TIME_ARG)
    private int eminus$cancelSectionFade(int relativeBuiltTime) {
        return NearFieldOverride.applied() ? NO_FADE : relativeBuiltTime;
    }
}
