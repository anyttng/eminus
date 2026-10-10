package com.eminus.gpu;

import java.util.Set;

import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.buffer.SparseBuffer;

public interface Sparse {
    long pageBytes();

    SparseBuffer reserve(String label, Set<BufferUsage> usage, long bytes);
}
