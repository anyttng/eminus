package com.eminus.render.far.client;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

public final class FarTarget implements AutoCloseable {
    private static final String COLOUR_LABEL = "eminus-far-colour";
    private static final String DEPTH_LABEL = "eminus-far-depth";
    private static final GpuFormat COLOUR_FORMAT = GpuFormat.RGBA8_UNORM;
    private static final int USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
    private static final int LAYERS = 1;
    private static final int MIPS = 1;

    private final GpuFormat depthStencilFormat;

    private GpuTexture colour;
    private GpuTexture depthStencil;
    private GpuTextureView colourView;
    private GpuTextureView depthStencilView;
    private int width;
    private int height;

    private FarTarget(GpuFormat depthStencilFormat, int width, int height) {
        this.depthStencilFormat = depthStencilFormat;
        allocate(width, height);
    }

    public static FarTarget create(GpuFormat depthStencilFormat, int width, int height) {
        RenderSystem.assertOnRenderThread();
        return new FarTarget(depthStencilFormat, width, height);
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

    public GpuTextureView depthStencilView() {
        return depthStencilView;
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
        depthStencil = device.createTexture(DEPTH_LABEL, USAGE, depthStencilFormat, width, height, LAYERS, MIPS);
        colourView = device.createTextureView(colour);
        depthStencilView = device.createTextureView(depthStencil);
    }

    private void free() {
        colourView.close();
        depthStencilView.close();
        colour.close();
        depthStencil.close();
    }
}
