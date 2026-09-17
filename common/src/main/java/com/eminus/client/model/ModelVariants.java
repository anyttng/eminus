package com.eminus.client.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.eminus.model.BakedModel;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

public final class ModelVariants implements AutoCloseable {
    public static final int BYTES = BakedModel.VARIANT_WORDS * Integer.BYTES;

    private static final String LABEL = "eminus-model-variants";
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST;

    private final GpuBuffer buffer;
    private final int capacity;

    private ModelVariants(GpuBuffer buffer, int capacity) {
        this.buffer = buffer;
        this.capacity = capacity;
    }

    public static ModelVariants create(int capacity) {
        RenderSystem.assertOnRenderThread();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> LABEL, USAGE, (long) capacity * BYTES);
        return new ModelVariants(buffer, capacity);
    }

    public GpuBuffer buffer() {
        return buffer;
    }

    public int capacity() {
        return capacity;
    }

    public void write(int start, int[] table) {
        RenderSystem.assertOnRenderThread();
        ByteBuffer scratch = ByteBuffer.allocateDirect(table.length * Integer.BYTES).order(ByteOrder.nativeOrder());
        for (int word : table) {
            scratch.putInt(word);
        }

        scratch.flip();
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(buffer.slice((long) start * BYTES, (long) table.length * Integer.BYTES), scratch);
    }

    @Override
    public void close() {
        buffer.close();
    }
}
