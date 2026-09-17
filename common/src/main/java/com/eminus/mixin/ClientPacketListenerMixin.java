package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;
import com.eminus.ingest.IngestTrigger;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.chunk.LevelChunk;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "enableChunkLight", at = @At("RETURN"))
    private void eminus$ingestLitChunk(LevelChunk chunk, int x, int z, CallbackInfo callback) {
        ClientSession.submitChunk(chunk, IngestTrigger.PACKET);
    }
}
