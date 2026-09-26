package com.eminus.mixin;

import java.util.BitSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;
import com.eminus.ingest.IngestTrigger;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.LevelChunk;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "enableChunkLight", at = @At("RETURN"))
    private void eminus$ingestLitChunk(LevelChunk chunk, int x, int z, CallbackInfo callback) {
        ClientSession.submitChunk(chunk, IngestTrigger.PACKET);
    }

    @ModifyArg(method = "handleLightUpdatePacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;queueLightUpdate(Ljava/lang/Runnable;)V"))
    private Runnable eminus$markLightPacket(Runnable apply,
            @Local(argsOnly = true) ClientboundLightUpdatePacket packet) {
        ClientLevel source = level;
        ClientboundLightUpdatePacketData lightData = packet.getLightData();
        BitSet sections = new BitSet();
        sections.or(lightData.getSkyYMask());
        sections.or(lightData.getEmptySkyYMask());
        sections.or(lightData.getBlockYMask());
        sections.or(lightData.getEmptyBlockYMask());
        return () -> {
            apply.run();
            ClientSession.lightPacketApplied(source, packet.getX(), packet.getZ(), sections);
        };
    }
}
