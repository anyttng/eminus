package com.eminus.beam;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.block.entity.BeaconBlockEntity;

import org.jspecify.annotations.Nullable;

public final class BeamWatch {
    public static final int LEVEL_PERIOD_TICKS = 80;

    private boolean scanned;
    private boolean settled;
    private @Nullable List<BeamSection> reported;

    public boolean settles(long gameTime, boolean scanEnded) {
        if (!settled && scanned && gameTime % LEVEL_PERIOD_TICKS == 0L) {
            settled = true;
        }
        if (scanEnded) {
            scanned = true;
        }

        return settled;
    }

    public void unsettle() {
        scanned = false;
        settled = false;
    }

    public @Nullable List<BeamSection> changed(List<BeaconBlockEntity.BeaconBeamSection> read) {
        List<BeamSection> last = reported;
        if (last != null && same(last, read)) {
            return null;
        }

        List<BeamSection> sections = new ArrayList<>(read.size());
        for (BeaconBlockEntity.BeaconBeamSection section : read) {
            sections.add(new BeamSection(section.getColor(), section.getHeight()));
        }
        reported = List.copyOf(sections);
        return reported;
    }

    private static boolean same(List<BeamSection> last, List<BeaconBlockEntity.BeaconBeamSection> read) {
        if (last.size() != read.size()) {
            return false;
        }

        for (int index = 0; index < last.size(); index++) {
            BeamSection was = last.get(index);
            BeaconBlockEntity.BeaconBeamSection now = read.get(index);
            if (was.argb() != now.getColor() || was.height() != now.getHeight()) {
                return false;
            }
        }

        return true;
    }
}
