package com.eminus.client.render.far;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

public final class FarTarget implements AutoCloseable {
    private static final String COLOUR_LABEL = "eminus-far-colour";
    private static final String DEPTH_LABEL = "eminus-far-depth";
    public static final GpuFormat COLOUR_FORMAT = GpuFormat.RGBA8_UNORM;
    private static final int USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
    private static final int LAYERS = 1;
    private static final int MIPS = 1;

    private final GpuFormat depthFormat;

    private GpuTexture colour;
    private GpuTexture depth;
    private GpuTextureView colourView;
    private GpuTextureView depthView;
    private int width;
    private int height;

    private FarTarget(GpuFormat depthFormat, int width, int height) {
        this.depthFormat = depthFormat;
        allocate(width, height);
    }

    public static FarTarget create(GpuFormat depthFormat, int width, int height) {
        RenderSystem.assertOnRenderThread();
        return new FarTarget(depthFormat, width, height);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public GpuTextureView colourView() {
        return colourView;
    }

    public GpuTextureView depthView() {
        return depthView;
    }

    public void resize(int width, int height) {
        RenderSystem.assertOnRenderThread();
        if (width == this.width && height == this.height) {
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
        GpuDevice device = RenderSystem.getDevice();
        this.width = width;
        this.height = height;
        colour = device.createTexture(COLOUR_LABEL, USAGE, COLOUR_FORMAT, width, height, LAYERS, MIPS);
        depth = device.createTexture(DEPTH_LABEL, USAGE, depthFormat, width, height, LAYERS, MIPS);
        colourView = device.createTextureView(colour);
        depthView = device.createTextureView(depth);
    }

    private void free() {
        colourView.close();
        depthView.close();
        colour.close();
        depth.close();
    }
}
