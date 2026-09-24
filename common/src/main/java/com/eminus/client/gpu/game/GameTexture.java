package com.eminus.client.gpu.game;

import com.eminus.gpu.Format;
import com.eminus.gpu.texture.Texture;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

final class GameTexture implements Texture {
    private static final int BASE_MIP = 0;

    private final GpuTexture texture;
    private final GpuTextureView view;
    private final Format format;
    private final boolean owned;

    private GameTexture(GpuTexture texture, GpuTextureView view, Format format, boolean owned) {
        this.texture = texture;
        this.view = view;
        this.format = format;
        this.owned = owned;
    }

    static GameTexture owned(GpuTexture texture, GpuTextureView view, Format format) {
        return new GameTexture(texture, view, format, true);
    }

    static GameTexture borrowed(GpuTextureView view) {
        return new GameTexture(view.texture(), view, GameTypes.format(view.texture().getFormat()), false);
    }

    GpuTexture texture() {
        return texture;
    }

    GpuTextureView view() {
        return view;
    }

    @Override
    public int width() {
        return view.getWidth(BASE_MIP);
    }

    @Override
    public int height() {
        return view.getHeight(BASE_MIP);
    }

    @Override
    public Format format() {
        return format;
    }

    @Override
    public void close() {
        if (owned) {
            view.close();
            texture.close();
        }
    }
}
