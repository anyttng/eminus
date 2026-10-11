package com.eminus.model.port;

@FunctionalInterface
public interface MipBlend {
    int blend(int first, int second, int third, int fourth, boolean cutout);
}
