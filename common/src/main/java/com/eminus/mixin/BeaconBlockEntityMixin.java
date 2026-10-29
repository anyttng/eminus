package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.beam.BeamWatch;
import com.eminus.client.beam.BeaconScan;
import com.eminus.client.session.ClientSession;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(BeaconBlockEntity.class)
public class BeaconBlockEntityMixin implements BeaconScan {
    @Shadow
    private int lastCheckY;

    @Unique
    private final BeamWatch eminus$watch = new BeamWatch();

    @Inject(method = "tick", at = @At("RETURN"))
    private static void eminus$readBeam(Level level, BlockPos pos, BlockState state, BeaconBlockEntity beacon,
            CallbackInfo callback) {
        if (level.isClientSide()) {
            ClientSession.beaconTicked(level, pos, beacon);
        }
    }

    @Unique
    @Override
    public int eminus$lastCheckY() {
        return lastCheckY;
    }

    @Unique
    @Override
    public BeamWatch eminus$beamWatch() {
        return eminus$watch;
    }
}
