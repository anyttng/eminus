package com.eminus.model;

import java.util.Arrays;

public final class Solidify {
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int RGB_MASK = 0x00FF_FFFF;
    private static final int UNREACHED = -1;

    public static void apply(int[] argb, int width, int height) {
        int size = width * height;
        int[] source = new int[size];
        Arrays.fill(source, UNREACHED);

        int[] queue = new int[size];
        int tail = 0;
        for (int index = 0; index < size; index++) {
            if ((argb[index] & ALPHA_MASK) != 0) {
                source[index] = index;
                queue[tail++] = index;
            }
        }

        if (tail == 0 || tail == size) {
            return;
        }

        for (int head = 0; head < tail; head++) {
            int index = queue[head];
            int drawn = source[index];
            int x = index % width;

            if (x > 0) {
                tail = spread(queue, tail, source, index - 1, drawn);
            }

            if (x < width - 1) {
                tail = spread(queue, tail, source, index + 1, drawn);
            }

            if (index >= width) {
                tail = spread(queue, tail, source, index - width, drawn);
            }

            if (index < size - width) {
                tail = spread(queue, tail, source, index + width, drawn);
            }
        }

        for (int index = 0; index < size; index++) {
            if (source[index] != UNREACHED && source[index] != index) {
                argb[index] = argb[index] & ALPHA_MASK | argb[source[index]] & RGB_MASK;
            }
        }
    }

    private static int spread(int[] queue, int tail, int[] source, int neighbour, int drawn) {
        if (source[neighbour] != UNREACHED) {
            return tail;
        }

        source[neighbour] = drawn;
        queue[tail] = neighbour;
        return tail + 1;
    }

    private Solidify() {
    }
}
