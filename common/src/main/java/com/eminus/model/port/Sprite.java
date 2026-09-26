package com.eminus.model.port;

public interface Sprite {
    int width();

    int height();

    float u0();

    float u1();

    float v0();

    float v1();

    int[] argb();

    default int index(float u, float v) {
        return texel(v, v0(), v1(), height()) * width() + texel(u, u0(), u1(), width());
    }

    private static int texel(float coordinate, float start, float end, int size) {
        float span = end - start;
        int index = span == 0.0F ? 0 : (int) ((coordinate - start) / span * size);
        return Math.clamp(index, 0, size - 1);
    }
}
