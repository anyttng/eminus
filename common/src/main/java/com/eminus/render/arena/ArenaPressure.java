package com.eminus.render.arena;

public final class ArenaPressure {
    public static final int HIGH_WATER_PERCENT = 85;
    public static final int LOW_WATER_PERCENT = 75;

    private static final int WHOLE_PERCENT = 100;

    private boolean holding;

    public boolean holding() {
        return holding;
    }

    public boolean update(int usedBlocks, int blocks, boolean refused) {
        boolean was = holding;
        long usedPercentOfBlocks = (long) usedBlocks * WHOLE_PERCENT;
        if (refused || usedPercentOfBlocks >= (long) blocks * HIGH_WATER_PERCENT) {
            holding = true;
        } else if (usedPercentOfBlocks < (long) blocks * LOW_WATER_PERCENT) {
            holding = false;
        }

        return holding && !was;
    }
}
