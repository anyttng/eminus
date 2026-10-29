package com.eminus.client.beam;

import java.util.List;

import com.eminus.beam.BeamSection;
import com.eminus.beam.BeamSet;
import com.eminus.beam.BeamWatch;

import it.unimi.dsi.fastutil.longs.LongList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public final class BeamTriggers {
    private static final int PYRAMID_REACH = 4;

    public static void ticked(BeamSet beams, Level level, BlockPos pos, BeaconBlockEntity beacon) {
        BeaconScan scan = (BeaconScan) beacon;
        BeamWatch watch = scan.eminus$beamWatch();
        if (!served(level, pos)) {
            watch.unsettle();
            return;
        }

        if (!watch.settles(level.getGameTime(), scan.eminus$lastCheckY() == level.getMinY() - 1)) {
            return;
        }

        List<BeamSection> changed = watch.changed(beacon.getBeamSections());
        if (changed != null) {
            beams.put(pos.asLong(), changed);
        }
    }

    public static void blockChanged(BeamSet beams, Level level, BlockPos pos) {
        if (beams.holds(pos.asLong()) && !(level.getBlockEntity(pos) instanceof BeaconBeamOwner)) {
            beams.remove(pos.asLong());
        }
    }

    public static void chunkArrived(BeamSet beams, LevelChunk chunk) {
        LongList held = beams.inChunk(chunk.getPos().x(), chunk.getPos().z());
        for (int index = 0; index < held.size(); index++) {
            long pos = held.getLong(index);
            if (!(chunk.getBlockEntities().get(BlockPos.of(pos)) instanceof BeaconBeamOwner)) {
                beams.remove(pos);
            }
        }
    }

    private static boolean served(Level level, BlockPos pos) {
        for (int chunkX = SectionPos.blockToSectionCoord(pos.getX() - PYRAMID_REACH);
                chunkX <= SectionPos.blockToSectionCoord(pos.getX() + PYRAMID_REACH); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(pos.getZ() - PYRAMID_REACH);
                    chunkZ <= SectionPos.blockToSectionCoord(pos.getZ() + PYRAMID_REACH); chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }

        return true;
    }

    private BeamTriggers() {
    }
}
