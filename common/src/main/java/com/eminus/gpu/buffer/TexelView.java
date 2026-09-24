package com.eminus.gpu.buffer;

import com.eminus.gpu.Format;

public interface TexelView extends AutoCloseable {
    Buffer buffer();

    Format format();

    @Override
    void close();
}
