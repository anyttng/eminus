package com.eminus.client.render.far;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

public final class FarTarget implements AutoCloseable {
    private static final String COLOUR_LABEL = "eminus-far-colour";
    private static final String DEPTH_LABEL = "eminus-far-depth";
    private static final String MASK_LABEL = "eminus-near-mask";
    public static final GpuFormat COLOUR_FORMAT = GpuFormat.RGBA8_UNORM;
    private static final int USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
    private static final int LAYERS = 1;
    private static final int MIPS = 1;

    private final GpuFormat depthStencilFormat;

    private GpuTexture colour;
    private GpuTexture depthStencil;
    private GpuTexture mask;
    private GpuTextureView colourView;
    private GpuTextureView depthStencilView;
    private GpuTextureView maskView;
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

    public GpuTextureView maskView() {
        return maskView;
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
        mask = device.createTexture(MASK_LABEL, USAGE, depthStencilFormat, width, height, LAYERS, MIPS);
        colourView = device.createTextureView(colour);
        depthStencilView = device.createTextureView(depthStencil);
        maskView = device.createTextureView(mask);
    }

    private void free() {
        colourView.close();
        depthStencilView.close();
        maskView.close();
        colour.close();
        depthStencil.close();
        mask.close();
    }
}
