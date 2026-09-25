package com.eminus.client.gpu;

import com.eminus.Eminus;
import com.eminus.client.gpu.game.GameGpu;
import com.eminus.client.gpu.opengl.OpenGlGpu;
import com.eminus.gpu.Gpu;

public final class Gpus {
    public static final String IMPLEMENTATION_PROPERTY = "eminus.gpu";
    public static final String GAME_IMPLEMENTATION = "game";

    private static final String OPENGL = "OpenGL";

    private Gpus() {
    }

    public static Gpu create() {
        String backend = GameGpu.backendName();
        boolean forced = GAME_IMPLEMENTATION.equals(System.getProperty(IMPLEMENTATION_PROPERTY));
        Gpu gpu = OPENGL.equals(backend) && !forced ? OpenGlGpu.create() : GameGpu.create();
        Eminus.LOGGER.info("[eminus-gl] implementation backend={} forced={} text={}", backend, forced,
                gpu.capabilities().backend());
        return gpu;
    }
}
