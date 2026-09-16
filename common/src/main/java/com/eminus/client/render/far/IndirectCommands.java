package com.eminus.client.render.far;

import com.eminus.render.far.DrawCommands;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;

public final class IndirectCommands implements AutoCloseable {
    private static final String LABEL = "eminus-indirect-commands";
    private static final int USAGE = GpuBuffer.USAGE_INDIRECT_PARAMETERS | GpuBuffer.USAGE_COPY_DST;

    private final GpuBuffer buffer;
    private final int capacity;

    private IndirectCommands(GpuBuffer buffer, int capacity) {
        this.buffer = buffer;
        this.capacity = capacity;
    }

    public static IndirectCommands create(int capacity) {
        RenderSystem.assertOnRenderThread();
        GpuBuffer buffer = RenderSystem.getDevice()
                .createBuffer(() -> LABEL, USAGE, (long) capacity * DrawCommands.COMMAND_BYTES);
        return new IndirectCommands(buffer, capacity);
    }

    public int capacity() {
        return capacity;
    }

    public void write(DrawCommands commands) {
        RenderSystem.assertOnRenderThread();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(range(0, commands.count()), commands.buffer());
    }

    public GpuBufferSlice range(int first, int count) {
        return buffer.slice((long) first * DrawCommands.COMMAND_BYTES, (long) count * DrawCommands.COMMAND_BYTES);
    }

    @Override
    public void close() {
        buffer.close();
    }
}
