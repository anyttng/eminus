package com.eminus.gpu.buffer;

public interface Buffer extends AutoCloseable {
    long size();

    @Override
    void close();
}
