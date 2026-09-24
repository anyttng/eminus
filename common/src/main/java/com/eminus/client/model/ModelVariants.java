package com.eminus.client.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.Set;

import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.model.BakedModel;

public final class ModelVariants implements AutoCloseable {
    public static final int BYTES = BakedModel.VARIANT_WORDS * Integer.BYTES;

    private static final String LABEL = "eminus-model-variants";
    private static final Set<BufferUsage> USAGE = EnumSet.of(BufferUsage.TEXEL, BufferUsage.COPY_DST);

    private final Gpu gpu;
    private final Buffer buffer;
    private final int capacity;

    private ModelVariants(Gpu gpu, Buffer buffer, int capacity) {
        this.gpu = gpu;
        this.buffer = buffer;
        this.capacity = capacity;
    }

    public static ModelVariants create(Gpu gpu, int capacity) {
        gpu.assertRenderThread();
        return new ModelVariants(gpu, gpu.buffer(LABEL, USAGE, (long) capacity * BYTES), capacity);
    }

    public Buffer buffer() {
        return buffer;
    }

    public int capacity() {
        return capacity;
    }

    public void write(int start, int[] table) {
        gpu.assertRenderThread();
        ByteBuffer scratch = ByteBuffer.allocateDirect(table.length * Integer.BYTES).order(ByteOrder.nativeOrder());
        for (int word : table) {
            scratch.putInt(word);
        }

        scratch.flip();
        gpu.write(buffer, (long) start * BYTES, scratch);
    }

    @Override
    public void close() {
        buffer.close();
    }
}
