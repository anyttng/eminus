package com.eminus.client.model.game;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

import org.jspecify.annotations.Nullable;

public final class ReplayRandom implements BitRandomSource {
    private final RandomSource source;
    private final IntArrayList drawn = new IntArrayList();
    private final MarsagliaPolarGaussian gaussian = new MarsagliaPolarGaussian(this);
    private @Nullable BitRandomSource reseeded;
    private int cursor;

    public ReplayRandom(RandomSource source) {
        this.source = source;
    }

    public void restart() {
        cursor = 0;
        reseeded = null;
        gaussian.reset();
    }

    @Override
    public int next(int bits) {
        if (reseeded != null) {
            return reseeded.next(bits);
        }

        if (cursor == drawn.size()) {
            drawn.add(source.nextInt());
        }

        return drawn.getInt(cursor++) >>> Integer.SIZE - bits;
    }

    @Override
    public RandomSource fork() {
        return new LegacyRandomSource(nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource.LegacyPositionalRandomFactory(nextLong());
    }

    @Override
    public void setSeed(long seed) {
        reseeded = new LegacyRandomSource(seed);
        gaussian.reset();
    }

    @Override
    public double nextGaussian() {
        return gaussian.nextGaussian();
    }
}
