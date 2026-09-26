package com.eminus.model.port;

import java.util.List;

import net.minecraft.util.RandomSource;

public interface BlockModel {
    void quads(RandomSource random, List<ModelQuad> into);

    List<Variant> variants();
}
