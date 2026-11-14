package com.eminus.mixin;

import com.eminus.client.session.SaveImports;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "save", at = @At("RETURN"))
    private void eminus$importSavedChunk(ChunkAccess chunk, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) {
            SaveImports.chunkSaved(level, chunk.getPos().x(), chunk.getPos().z());
        }
    }
}
