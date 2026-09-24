package com.eminus.gpu.buffer;

import java.nio.ByteBuffer;

import org.jspecify.annotations.Nullable;

public interface Staging extends AutoCloseable {
    Step step();

    @Override
    void close();

    interface Step extends AutoCloseable {
        @Nullable Staged stage(ByteBuffer bytes);

        void copy(Staged staged, Buffer target, long offset);

        @Override
        void close();
    }

    interface Staged {
    }
}
