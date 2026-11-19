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
    private static final String FRONT_DEPTH_LABEL = "eminus-far-front-depth";
    private static final String SECOND_DEPTH_LABEL = "eminus-far-second-depth";
    private static final String OCCLUDER_DEPTH_LABEL = "eminus-far-occluder-depth";
    private static final String MERGED_OPAQUE_DEPTH_LABEL = "eminus-merged-opaque-depth";
    private static final String MERGED_DEPTH_LABEL = "eminus-merged-depth";
    public static final Format COLOR_FORMAT = Format.RGBA8_UNORM;
    private static final Set<TextureUsage> USAGE = EnumSet.of(TextureUsage.ATTACHMENT, TextureUsage.SAMPLED);
    private static final Set<TextureUsage> DEPTH_USAGE = EnumSet.of(TextureUsage.ATTACHMENT, TextureUsage.SAMPLED,
            TextureUsage.COPY_SRC);
    private static final int MIPS = 1;

    private final Gpu gpu;
    private final Format depthFormat;

    private Texture color;
    private Texture depth;
    private Texture frontDepth;
    private Texture secondDepth;
    private @Nullable Texture opaqueDepth;
    private @Nullable Texture occluderDepth;
    private @Nullable Texture mergedOpaqueDepth;
    private @Nullable Texture mergedDepth;

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

    public Texture frontDepth() {
        return frontDepth;
    }

    public Texture secondDepth() {
        return secondDepth;
    }

    public Texture opaqueDepth() {
        if (opaqueDepth == null) {
            opaqueDepth = gpu.texture(OPAQUE_DEPTH_LABEL, USAGE, depthFormat, width(), height(), MIPS);
        }
        return opaqueDepth;
    }

    public Texture occluderDepth() {
        if (occluderDepth == null) {
            occluderDepth = gpu.texture(OCCLUDER_DEPTH_LABEL, DEPTH_USAGE, depthFormat, width(), height(), MIPS);
        }
        return occluderDepth;
    }

    public Texture mergedOpaqueDepth() {
        if (mergedOpaqueDepth == null) {
            mergedOpaqueDepth = gpu.texture(MERGED_OPAQUE_DEPTH_LABEL, USAGE, MergedDepthPass.FORMAT, width(),
                    height(), MIPS);
        }
        return mergedOpaqueDepth;
    }

    public Texture mergedDepth() {
        if (mergedDepth == null) {
            mergedDepth = gpu.texture(MERGED_DEPTH_LABEL, USAGE, MergedDepthPass.FORMAT, width(), height(), MIPS);
        }
        return mergedDepth;
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
        depth = gpu.texture(DEPTH_LABEL, DEPTH_USAGE, depthFormat, width, height, MIPS);
        frontDepth = gpu.texture(FRONT_DEPTH_LABEL, USAGE, depthFormat, width, height, MIPS);
        secondDepth = gpu.texture(SECOND_DEPTH_LABEL, USAGE, depthFormat, width, height, MIPS);
    }

    private void free() {
        color.close();
        depth.close();
        frontDepth.close();
        secondDepth.close();
        if (opaqueDepth != null) {
            opaqueDepth.close();
            opaqueDepth = null;
        }
        if (occluderDepth != null) {
            occluderDepth.close();
            occluderDepth = null;
        }
        if (mergedOpaqueDepth != null) {
            mergedOpaqueDepth.close();
            mergedOpaqueDepth = null;
        }
        if (mergedDepth != null) {
            mergedDepth.close();
            mergedDepth = null;
        }
    }
}
