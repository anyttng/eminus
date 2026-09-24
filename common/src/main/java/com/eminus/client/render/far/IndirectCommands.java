package com.eminus.client.render.far;

import java.util.EnumSet;
import java.util.Set;

import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.render.far.DrawCommands;

public final class IndirectCommands implements AutoCloseable {
    private static final String LABEL = "eminus-indirect-commands";
    private static final Set<BufferUsage> USAGE = EnumSet.of(BufferUsage.INDIRECT, BufferUsage.COPY_DST);
    private static final int START_OF_BUFFER = 0;

    private final Gpu gpu;
    private final Buffer buffer;
    private final int capacity;

    private IndirectCommands(Gpu gpu, Buffer buffer, int capacity) {
        this.gpu = gpu;
        this.buffer = buffer;
        this.capacity = capacity;
    }

    public static IndirectCommands create(Gpu gpu, int capacity) {
        gpu.assertRenderThread();
        return new IndirectCommands(gpu, gpu.buffer(LABEL, USAGE, (long) capacity * DrawCommands.COMMAND_BYTES),
                capacity);
    }

    public int capacity() {
        return capacity;
    }

    public void write(DrawCommands commands) {
        gpu.assertRenderThread();
        gpu.write(buffer, START_OF_BUFFER, commands.buffer());
    }

    public Buffer buffer() {
        return buffer;
    }

    @Override
    public void close() {
        buffer.close();
    }
}
