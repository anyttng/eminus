package com.eminus.gpu.texture;

import com.eminus.gpu.Format;

public interface Texture extends AutoCloseable {
    int width();

    int height();

    Format format();

    @Override
    void close();
}
