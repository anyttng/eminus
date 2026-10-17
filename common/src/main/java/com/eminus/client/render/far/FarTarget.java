package com.eminus.client.render.far;

import java.util.EnumSet;
import java.util.Set;

import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.texture.Texture;
import com.eminus.gpu.texture.TextureUsage;

import org.jspecify.annotations.Nullable;

public final class FarTarget implements AutoCloseable {
    private static final String COLOR_LABEL = "eminus-far-color";
    private static final String DEPTH_LABEL = "eminus-far-depth";
    private static final String OPAQUE_DEPTH_LABEL = "eminus-far-opaque-depth";
    public static final Format COLOR_FORMAT = Format.RGBA8_UNORM;
    private static final Set<TextureUsage> USAGE = EnumSet.of(TextureUsage.ATTACHMENT, TextureUsage.SAMPLED);
    private static final int MIPS = 1;

    private final Gpu gpu;
    private final Format depthFormat;

    private Texture color;
    private Texture depth;
    private @Nullable Texture opaqueDepth;

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
        return color.width();
    }

    public int height() {
        return color.height();
    }

    public Texture color() {
        return color;
    }

    public Texture depth() {
        return depth;
    }

    public Texture opaqueDepth() {
        if (opaqueDepth == null) {
            opaqueDepth = gpu.texture(OPAQUE_DEPTH_LABEL, USAGE, depthFormat, width(), height(), MIPS);
        }
        return opaqueDepth;
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
        color = gpu.texture(COLOR_LABEL, USAGE, COLOR_FORMAT, width, height, MIPS);
        depth = gpu.texture(DEPTH_LABEL, USAGE, depthFormat, width, height, MIPS);
    }

    private void free() {
        color.close();
        depth.close();
        if (opaqueDepth != null) {
            opaqueDepth.close();
            opaqueDepth = null;
        }
    }
}
