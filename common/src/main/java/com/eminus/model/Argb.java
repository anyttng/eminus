package com.eminus.model;

public final class Argb {
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int CHANNEL_MAX = 255;
    private static final double GAMMA = 2.2;
    private static final double INVERSE_GAMMA = 0.45454545454545453;
    private static final double QUARTER = 0.25;
    private static final int CORNERS = 4;
    private static final int CUTOUT_ALPHA = 96;

    private static final float[] POW22 = pow22();

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

    public static int meanGamma(int first, int second, int third, int fourth) {
        return gammaMean(first, second, third, fourth, ALPHA_SHIFT) << ALPHA_SHIFT
                | gammaMean(first, second, third, fourth, RED_SHIFT) << RED_SHIFT
                | gammaMean(first, second, third, fourth, GREEN_SHIFT) << GREEN_SHIFT
                | gammaMean(first, second, third, fourth, 0);
    }

    private static int gammaMean(int first, int second, int third, int fourth, int shift) {
        float sum = POW22[channel(first, shift)] + POW22[channel(second, shift)] + POW22[channel(third, shift)]
                + POW22[channel(fourth, shift)];
        float mean = (float) Math.pow(sum * QUARTER, INVERSE_GAMMA);
        return (int) (mean * (double) CHANNEL_MAX);
    }

    public static int alphaBlend(int first, int second, int third, int fourth) {
        int alpha = drawnMean(first, second, third, fourth, ALPHA_SHIFT);
        return (alpha < CUTOUT_ALPHA ? 0 : alpha) << ALPHA_SHIFT
                | drawnMean(first, second, third, fourth, RED_SHIFT) << RED_SHIFT
                | drawnMean(first, second, third, fourth, GREEN_SHIFT) << GREEN_SHIFT
                | drawnMean(first, second, third, fourth, 0);
    }

    private static int drawnMean(int first, int second, int third, int fourth, int shift) {
        float total = 0.0F;
        total += drawnPow22(first, shift);
        total += drawnPow22(second, shift);
        total += drawnPow22(third, shift);
        total += drawnPow22(fourth, shift);
        return (int) (Math.pow(total / CORNERS, INVERSE_GAMMA) * CHANNEL_MAX);
    }

    private static float drawnPow22(int argb, int shift) {
        return channel(argb, ALPHA_SHIFT) == 0 ? 0.0F : POW22[channel(argb, shift)];
    }

    private static int channelProduct(int first, int second, int shift) {
        return channel(first, shift) * channel(second, shift) / CHANNEL_MAX;
    }

    private static int channel(int argb, int shift) {
        return argb >>> shift & CHANNEL_MASK;
    }

    private static float[] pow22() {
        float[] table = new float[CHANNEL_MAX + 1];
        for (int index = 0; index < table.length; index++) {
            table[index] = (float) Math.pow(index / (float) CHANNEL_MAX, GAMMA);
        }

        return table;
    }

    private Argb() {
    }
}
