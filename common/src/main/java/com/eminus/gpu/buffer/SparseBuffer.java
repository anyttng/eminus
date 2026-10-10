package com.eminus.gpu.buffer;

public interface SparseBuffer extends Buffer {
    void commit(long offset, long bytes);

    void decommit(long offset, long bytes);
}
