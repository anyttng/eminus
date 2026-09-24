package com.eminus.client.gpu.game;

import com.eminus.gpu.buffer.Buffer;

import com.mojang.blaze3d.buffers.GpuBuffer;

record GameBuffer(GpuBuffer buffer) implements Buffer {
    @Override
    public long size() {
        return buffer.size();
    }

    @Override
    public void close() {
        buffer.close();
    }
}
