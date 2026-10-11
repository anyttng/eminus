package com.eminus.model;

public final class Argb {
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int CHANNEL_MAX = 255;

    public static int opaque(int argb) {
        return argb | ALPHA_MASK;
    }

    public static int multiply(int first, int second) {
        if (first == -1) {
            return second;
        }

        if (second == -1) {
            return first;
        }

        return channelProduct(first, second, ALPHA_SHIFT) << ALPHA_SHIFT
                | channelProduct(first, second, RED_SHIFT) << RED_SHIFT
                | channelProduct(first, second, GREEN_SHIFT) << GREEN_SHIFT
                | channelProduct(first, second, 0);
    }

    private static int channelProduct(int first, int second, int shift) {
        return channel(first, shift) * channel(second, shift) / CHANNEL_MAX;
    }

    private static int channel(int argb, int shift) {
        return argb >>> shift & CHANNEL_MASK;
    }

    private Argb() {
    }
}
