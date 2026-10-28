package com.eminus.render.arena;

import it.unimi.dsi.fastutil.longs.LongArrayList;

public final class ArenaAllocator {
    public static final int QUADS_PER_SEGMENT = 256;
    public static final int NO_SEGMENT = -1;

    private static final int START_SHIFT = 32;
    private static final long LENGTH_MASK = 0xFFFFFFFFL;

    private final LongArrayList free = new LongArrayList();

    private int segments;

    private int used;

    public ArenaAllocator(int segments) {
        if (segments <= 0) {
            throw new IllegalArgumentException("An arena of " + segments + " segments holds nothing.");
        }

        this.segments = segments;
        free.add(range(0, segments));
    }

    public static int segmentsFor(int quads) {
        return (quads + QUADS_PER_SEGMENT - 1) / QUADS_PER_SEGMENT;
    }

    public int segments() {
        return segments;
    }

    public int usedSegments() {
        return used;
    }

    public int freeSegments() {
        return segments - used;
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

    public void grow(int segments) {
        if (segments <= this.segments) {
            throw new IllegalArgumentException(
                    "An arena of " + this.segments + " segments does not grow to " + segments + " segments.");
        }

        free.add(range(this.segments, segments - this.segments));
        this.segments = segments;
        if (free.size() > 1) {
            mergeWithNext(free.size() - 2);
        }
    }

    public int allocate(int quads) {
        int wanted = segmentsFor(quads);
        if (wanted == 0) {
            return NO_SEGMENT;
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

        return NO_SEGMENT;
    }

    public void free(int start, int quads) {
        int length = segmentsFor(quads);
        if (length == 0) {
            return;
        }

        if (start < 0 || start + length > segments) {
            throw new IllegalArgumentException(
                    "Segment range " + start + ".." + (start + length) + " is outside an arena of " + segments + " segments.");
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
