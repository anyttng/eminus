package com.eminus.model;

public final class Mips {
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int CORNERS = 4;

    public static int levelCount(int side) {
        return Integer.numberOfTrailingZeros(side) + 1;
    }

    public static int[][] chain(int[] face, int side) {
        int[][] levels = new int[levelCount(side)][];
        levels[0] = face;

        for (int level = 1; level < levels.length; level++) {
            levels[level] = halve(levels[level - 1], side >> level - 1);
        }

        return levels;
    }

    private static int[] halve(int[] source, int side) {
        int half = side >> 1;
        int[] target = new int[half * half];

        for (int y = 0; y < half; y++) {
            int top = y * 2 * side;
            int bottom = top + side;

            for (int x = 0; x < half; x++) {
                int left = x * 2;
                target[y * half + x] = average(
                        source[top + left], source[top + left + 1],
                        source[bottom + left], source[bottom + left + 1]);
            }
        }

        return target;
    }

    private static int average(int first, int second, int third, int fourth) {
        return channel(first, second, third, fourth, ALPHA_SHIFT) << ALPHA_SHIFT
                | channel(first, second, third, fourth, RED_SHIFT) << RED_SHIFT
                | channel(first, second, third, fourth, GREEN_SHIFT) << GREEN_SHIFT
                | channel(first, second, third, fourth, 0);
    }

    private static int channel(int first, int second, int third, int fourth, int shift) {
        int sum = (first >>> shift & CHANNEL_MASK)
                + (second >>> shift & CHANNEL_MASK)
                + (third >>> shift & CHANNEL_MASK)
                + (fourth >>> shift & CHANNEL_MASK);
        return (sum + CORNERS / 2) / CORNERS;
    }

    private Mips() {
    }
}
