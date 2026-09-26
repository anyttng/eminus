package com.eminus.client.gpu;

import com.eminus.Eminus;
import com.eminus.client.gpu.opengl.OpenGlGpu;
import com.eminus.gpu.Gpu;

public final class Gpus {
    private Gpus() {
    }

    public static Gpu create() {
        Gpu gpu = OpenGlGpu.create();
        Eminus.LOGGER.info("[eminus-gl] implementation text={}", gpu.capabilities().backend());
        return gpu;
    }
}
