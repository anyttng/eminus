package com.eminus.mixin;

import com.eminus.client.session.SaveImports;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {
    @Shadow
    @Final
    ServerLevel level;

    @ModifyExpressionValue(method = "save", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;write("
                    + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)"
                    + "Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag eminus$importSavedChunk(CompoundTag snapshot) {
        SaveImports.chunkSaved(level, snapshot);
        return snapshot;
    }
}
