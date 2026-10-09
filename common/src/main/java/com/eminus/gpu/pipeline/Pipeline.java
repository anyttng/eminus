package com.eminus.gpu.pipeline;

import com.eminus.gpu.Location;

public interface Pipeline {
    Location location();

    boolean compiles();
}
