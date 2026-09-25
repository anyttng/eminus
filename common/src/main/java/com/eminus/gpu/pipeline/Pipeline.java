package com.eminus.gpu.pipeline;

import net.minecraft.resources.Identifier;

public interface Pipeline {
    Identifier location();

    boolean compiles();
}
