package com.eminus.render.arena;

import it.unimi.dsi.fastutil.longs.LongArrayList;

public final class ArenaAllocator {
    public static final int QUADS_PER_BLOCK = 256;
    public static final int NO_BLOCK = -1;

    private static final int START_SHIFT = 32;
    private static final long LENGTH_MASK = 0xFFFFFFFFL;

    private final int blocks;
    private final LongArrayList free = new LongArrayList();

    private int used;

    public ArenaAllocator(int blocks) {
        if (blocks <= 0) {
            throw new IllegalArgumentException("An arena of " + blocks + " blocks holds nothing.");
        }

        this.blocks = blocks;
        free.add(range(0, blocks));
    }

    public static int blocksFor(int quads) {
        return (quads + QUADS_PER_BLOCK - 1) / QUADS_PER_BLOCK;
    }

    public int blocks() {
        return blocks;
    }

    public int usedBlocks() {
        return used;
    }

    public int freeBlocks() {
        return blocks - used;
    }

    public int largestFreeRun() {
        int largest = 0;

        for (int index = 0; index < free.size(); index++) {
            largest = Math.max(largest, length(free.getLong(index)));
        }

        return largest;
    }

    public int freeRuns() {
        return free.size();
    }

    public int allocate(int quads) {
        int wanted = blocksFor(quads);
        if (wanted == 0) {
            return NO_BLOCK;
        }

        for (int index = 0; index < free.size(); index++) {
            long candidate = free.getLong(index);
            int length = length(candidate);
            if (length < wanted) {
                continue;
            }

            int start = start(candidate);
            if (length == wanted) {
                free.removeLong(index);
            } else {
                free.set(index, range(start + wanted, length - wanted));
            }

            used += wanted;
            return start;
        }

        return NO_BLOCK;
    }

    public void free(int start, int quads) {
        int length = blocksFor(quads);
        if (length == 0) {
            return;
        }

        if (start < 0 || start + length > blocks) {
            throw new IllegalArgumentException(
                    "Block range " + start + ".." + (start + length) + " is outside an arena of " + blocks + " blocks.");
        }

        int index = insertionPoint(start);
        free.add(index, range(start, length));
        used -= length;

        if (index + 1 < free.size()) {
            mergeWithNext(index);
        }

        if (index > 0) {
            mergeWithNext(index - 1);
        }
    }

    private int insertionPoint(int start) {
        for (int index = 0; index < free.size(); index++) {
            if (start(free.getLong(index)) > start) {
                return index;
            }
        }

        return free.size();
    }

    private void mergeWithNext(int index) {
        long left = free.getLong(index);
        long right = free.getLong(index + 1);

        if (start(left) + length(left) == start(right)) {
            free.set(index, range(start(left), length(left) + length(right)));
            free.removeLong(index + 1);
        }
    }

    private static long range(int start, int length) {
        return ((long) start << START_SHIFT) | (length & LENGTH_MASK);
    }

    private static int start(long range) {
        return (int) (range >>> START_SHIFT);
    }

    private static int length(long range) {
        return (int) (range & LENGTH_MASK);
    }
}
