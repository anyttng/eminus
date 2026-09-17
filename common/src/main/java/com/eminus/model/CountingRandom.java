package com.eminus.model;

import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

final class CountingRandom extends SingleThreadedRandomSource {
    private int draws;

    CountingRandom() {
        super(0L);
    }

    @Override
    public int next(int bits) {
        draws++;
        return super.next(bits);
    }

    void restart(long seed) {
        setSeed(seed);
        draws = 0;
    }

    boolean drew() {
        return draws > 0;
    }
}
