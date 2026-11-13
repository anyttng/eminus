package com.eminus.compat.sable;

import com.eminus.ingest.ChunkRefusals;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.world.level.Level;

public final class SablePlots {
    public static void register() {
        if (SableMixinPlugin.sablePresent()) {
            ChunkRefusals.register(SablePlots::inPlot);
        }
    }

    private static boolean inPlot(Level level, int chunkX, int chunkZ) {
        SubLevelContainer plots = SubLevelContainer.getContainer(level);
        return plots != null && plots.inBounds(chunkX, chunkZ);
    }

    private SablePlots() {
    }
}
