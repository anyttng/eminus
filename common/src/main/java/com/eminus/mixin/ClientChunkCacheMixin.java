package com.eminus.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.eminus.client.session.ClientSession;
import com.eminus.ingest.IngestTrigger;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "onLightUpdate", at = @At("RETURN"))
    private void eminus$markLightUpdate(LightLayer layer, SectionPos pos, CallbackInfo callback) {
        ClientSession.lightUpdated(level, pos);
    }

    @Inject(method = "drop", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;replace(I"
                    + "Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/chunk/LevelChunk;)"
                    + "Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private void eminus$unloadDropped(ChunkPos pos, CallbackInfo callback, @Local LevelChunk chunk) {
        ClientSession.submitChunk(chunk, IngestTrigger.UNLOAD);
    }

    @Inject(method = "replaceWithPacketData", at = @At(value = "NEW",
            target = "net/minecraft/world/level/chunk/LevelChunk"))
    private void eminus$unloadReplaced(int chunkX, int chunkZ, FriendlyByteBuf readBuffer,
            CompoundTag heightmaps,
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities,
            CallbackInfoReturnable<LevelChunk> callback, @Local LevelChunk chunk) {
        if (chunk != null) {
            ClientSession.submitChunk(chunk, IngestTrigger.UNLOAD);
        }
    }

    @ModifyExpressionValue(method = "updateViewRadius", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;inRange(II)Z"))
    private boolean eminus$unloadOutOfRange(boolean inRange, @Local LevelChunk chunk) {
        if (!inRange) {
            ClientSession.submitChunk(chunk, IngestTrigger.UNLOAD);
        }

        return inRange;
    }
}
