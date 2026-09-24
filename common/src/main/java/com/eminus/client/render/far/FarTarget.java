package com.eminus.client.render.far;

import java.util.EnumSet;
import java.util.Set;

import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.texture.Texture;
import com.eminus.gpu.texture.TextureUsage;

public final class FarTarget implements AutoCloseable {
    private static final String COLOUR_LABEL = "eminus-far-colour";
    private static final String DEPTH_LABEL = "eminus-far-depth";
    public static final Format COLOUR_FORMAT = Format.RGBA8_UNORM;
    private static final Set<TextureUsage> USAGE = EnumSet.of(TextureUsage.ATTACHMENT, TextureUsage.SAMPLED);
    private static final int MIPS = 1;

    private final Gpu gpu;
    private final Format depthFormat;

    private Texture colour;
    private Texture depth;

    private FarTarget(Gpu gpu, Format depthFormat, int width, int height) {
        this.gpu = gpu;
        this.depthFormat = depthFormat;
        allocate(width, height);
    }

    public static FarTarget create(Gpu gpu, Format depthFormat, int width, int height) {
        gpu.assertRenderThread();
        return new FarTarget(gpu, depthFormat, width, height);
    }

    public int width() {
        return colour.width();
    }

    public int height() {
        return colour.height();
    }

    public Texture colour() {
        return colour;
    }

    public Texture depth() {
        return depth;
    }

    public void resize(int width, int height) {
        gpu.assertRenderThread();
        if (width == width() && height == height()) {
            return;
        }

        free();
        allocate(width, height);
    }

    @Override
    public void close() {
        free();
    }

    private void allocate(int width, int height) {
        colour = gpu.texture(COLOUR_LABEL, USAGE, COLOUR_FORMAT, width, height, MIPS);
        depth = gpu.texture(DEPTH_LABEL, USAGE, depthFormat, width, height, MIPS);
    }

    private void free() {
        colour.close();
        depth.close();
    }
}
