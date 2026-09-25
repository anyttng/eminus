package com.eminus.gpu.pass;

import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.pipeline.Pipeline;

public interface Pass extends Bindings, AutoCloseable {
    int INDEXED_INDIRECT_BYTES = 5 * Integer.BYTES;

    void pipeline(Pipeline pipeline);

    void bindGameGlobals();

    void quadIndices(int maxIndices);

    void draw(int vertices);

    void drawIndexedIndirect(Buffer commands, int firstCommand, int count);

    @Override
    void close();
}
