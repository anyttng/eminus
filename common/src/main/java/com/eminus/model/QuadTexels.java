package com.eminus.model;

import com.eminus.model.port.ModelQuad;

@FunctionalInterface
public interface QuadTexels {
    int argb(ModelQuad quad, float u, float v);
}
