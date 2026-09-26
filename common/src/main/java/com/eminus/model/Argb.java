package com.eminus.model;

public final class Argb {
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int CHANNEL_MAX = 255;
    private static final int CORNERS = 4;
    private static final int LINEAR_DEPTH = 1024;
    private static final float LINEAR_MAX = LINEAR_DEPTH - 1;
    private static final float SRGB_KNEE = 0.04045F;
    private static final float LINEAR_KNEE = 0.0031308F;
    private static final float LINEAR_SLOPE = 12.92F;
    private static final double GAMMA_OFFSET = 0.055;
    private static final double GAMMA_SCALE = 1.055;
    private static final double GAMMA = 2.4;
    private static final double INVERSE_GAMMA = 0.4166666666666667;

    private static final short[] SRGB_TO_LINEAR = srgbToLinear();
    private static final byte[] LINEAR_TO_SRGB = linearToSrgb();

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

    public static int meanLinear(int first, int second, int third, int fourth) {
        int alpha = (channel(first, ALPHA_SHIFT) + channel(second, ALPHA_SHIFT) + channel(third, ALPHA_SHIFT)
                + channel(fourth, ALPHA_SHIFT)) / CORNERS;
        return alpha << ALPHA_SHIFT
                | linearMean(first, second, third, fourth, RED_SHIFT) << RED_SHIFT
                | linearMean(first, second, third, fourth, GREEN_SHIFT) << GREEN_SHIFT
                | linearMean(first, second, third, fourth, 0);
    }

    private static int linearMean(int first, int second, int third, int fourth, int shift) {
        int linear = (SRGB_TO_LINEAR[channel(first, shift)] + SRGB_TO_LINEAR[channel(second, shift)]
                + SRGB_TO_LINEAR[channel(third, shift)] + SRGB_TO_LINEAR[channel(fourth, shift)]) / CORNERS;
        return LINEAR_TO_SRGB[linear] & CHANNEL_MASK;
    }

    private static int channelProduct(int first, int second, int shift) {
        return channel(first, shift) * channel(second, shift) / CHANNEL_MAX;
    }

    private static int channel(int argb, int shift) {
        return argb >>> shift & CHANNEL_MASK;
    }

    private static short[] srgbToLinear() {
        short[] table = new short[CHANNEL_MAX + 1];
        for (int index = 0; index < table.length; index++) {
            float x = index / (float) CHANNEL_MAX;
            float linear = x >= SRGB_KNEE ? (float) Math.pow((x + GAMMA_OFFSET) / GAMMA_SCALE, GAMMA)
                    : x / LINEAR_SLOPE;
            table[index] = (short) Math.round(linear * LINEAR_MAX);
        }

        return table;
    }

    private static byte[] linearToSrgb() {
        byte[] table = new byte[LINEAR_DEPTH];
        for (int index = 0; index < table.length; index++) {
            float x = index / LINEAR_MAX;
            float srgb = x >= LINEAR_KNEE ? (float) (GAMMA_SCALE * Math.pow(x, INVERSE_GAMMA) - GAMMA_OFFSET)
                    : LINEAR_SLOPE * x;
            table[index] = (byte) Math.round(srgb * CHANNEL_MAX);
        }

        return table;
    }

    private Argb() {
    }
}
