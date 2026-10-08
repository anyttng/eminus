package com.eminus.cell;

import java.util.Arrays;

public final class IdTable {
    private static final int GROWTH = 2;

    private final int missing;

    private volatile int[] values;

    public IdTable(int capacity, int missing) {
        this.missing = missing;
        values = new int[capacity];
        Arrays.fill(values, missing);
    }

    public int get(int id) {
        int[] snapshot = values;
        return id >= 0 && id < snapshot.length ? snapshot[id] : missing;
    }

    public synchronized void put(int id, int value) {
        int[] current = values;
        if (id >= current.length) {
            int known = current.length;
            current = Arrays.copyOf(current, grownLength(known, id));
            Arrays.fill(current, known, current.length, missing);
        }

        current[id] = value;
        values = current;
    }

    public static int grownLength(int length, int id) {
        return Math.max(length * GROWTH, id + 1);
    }
}
