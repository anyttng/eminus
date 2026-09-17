package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "onLightUpdate", at = @At("RETURN"))
    private void eminus$markLightUpdate(LightLayer layer, SectionPos pos, CallbackInfo callback) {
        ClientSession.lightUpdated(level, pos);
    }
}
