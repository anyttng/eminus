package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.session.client.ClientSession;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("RETURN"))
    private void eminus$markChangedSection(BlockPos pos, BlockState old, BlockState current, int updateFlags,
            CallbackInfo callback) {
        ClientSession.blockChanged((ClientLevel) (Object) this, pos);
    }
}
