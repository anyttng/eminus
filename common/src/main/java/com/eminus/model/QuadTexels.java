package com.eminus.model;

import net.minecraft.client.resources.model.geometry.BakedQuad;

@FunctionalInterface
public interface QuadTexels {
    int argb(BakedQuad quad, float u, float v);
}
