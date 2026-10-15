package com.eminus.mesh;

public final class OcclusionWord {
    public static final int BITS = 9;
    public static final int PER_SLOT = Long.SIZE / BITS;
    public static final int NONE = 0;
    public static final int FRONT = 1;
    public static final int LOW_U = 1 << 1;
    public static final int HIGH_U = 1 << 2;
    public static final int LOW_V = 1 << 3;
    public static final int HIGH_V = 1 << 4;
    public static final int LOW_U_LOW_V = 1 << 5;
    public static final int HIGH_U_LOW_V = 1 << 6;
    public static final int LOW_U_HIGH_V = 1 << 7;
    public static final int HIGH_U_HIGH_V = 1 << 8;

    private static final long MASK = (1L << BITS) - 1;
    private static final int HIGH_U_END = HIGH_U | HIGH_U_LOW_V | HIGH_U_HIGH_V;
    private static final int HIGH_V_END = HIGH_V | LOW_U_HIGH_V | HIGH_U_HIGH_V;

    public static int slots(int quads) {
        return Math.ceilDiv(quads, PER_SLOT);
    }

    public static long[] pack(int[] words, int count) {
        long[] slots = new long[slots(count)];
        for (int index = 0; index < count; index++) {
            slots[index / PER_SLOT] |= (words[index] & MASK) << (index % PER_SLOT * BITS);
        }

        return slots;
    }

    public static int unpack(long[] slots, int index) {
        return (int) (slots[index / PER_SLOT] >>> (index % PER_SLOT * BITS) & MASK);
    }

    public static boolean joinsAlongU(int low, int high) {
        return same(low, high, FRONT | LOW_V | HIGH_V)
                && matches(low, HIGH_U, FRONT) && matches(low, HIGH_U_LOW_V, LOW_V)
                && matches(low, HIGH_U_HIGH_V, HIGH_V) && matches(high, LOW_U, FRONT)
                && matches(high, LOW_U_LOW_V, LOW_V) && matches(high, LOW_U_HIGH_V, HIGH_V);
    }

    public static int joinAlongU(int low, int high) {
        return (low & ~HIGH_U_END) | (high & HIGH_U_END);
    }

    public static boolean joinsAlongV(int low, int high) {
        return same(low, high, FRONT | LOW_U | HIGH_U)
                && matches(low, HIGH_V, FRONT) && matches(low, LOW_U_HIGH_V, LOW_U)
                && matches(low, HIGH_U_HIGH_V, HIGH_U) && matches(high, LOW_V, FRONT)
                && matches(high, LOW_U_LOW_V, LOW_U) && matches(high, HIGH_U_LOW_V, HIGH_U);
    }

    public static int joinAlongV(int low, int high) {
        return (low & ~HIGH_V_END) | (high & HIGH_V_END);
    }

    private static boolean same(int first, int second, int bits) {
        return (first & bits) == (second & bits);
    }

    private static boolean matches(int word, int bit, int other) {
        return ((word & bit) != 0) == ((word & other) != 0);
    }

    private OcclusionWord() {
    }
}
