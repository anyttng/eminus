package com.eminus.client.gpu.game;

import com.eminus.gpu.Format;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;

record GameTexelView(Buffer buffer, Format format) implements TexelView {
    @Override
    public void close() {
    }
}
