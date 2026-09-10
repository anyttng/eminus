package com.eminus.render.arena.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.eminus.render.arena.MeshSlot;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

public final class MeshRecords implements AutoCloseable {
    public static final int TEXELS = 1;
    public static final int BYTES = TEXELS * 4 * Integer.BYTES;

    private static final String LABEL = "eminus-mesh-records";
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST;
    private static final int KEY_SHIFT = 32;

    private final GpuBuffer buffer;
    private final int capacity;
    private final ByteBuffer scratch = ByteBuffer.allocateDirect(BYTES).order(ByteOrder.nativeOrder());

    private MeshRecords(GpuBuffer buffer, int capacity) {
        this.buffer = buffer;
        this.capacity = capacity;
    }

    public static MeshRecords create(int capacity) {
        RenderSystem.assertOnRenderThread();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> LABEL, USAGE, (long) capacity * BYTES);
        return new MeshRecords(buffer, capacity);
    }

    public GpuBuffer buffer() {
        return buffer;
    }

    public int capacity() {
        return capacity;
    }

    public void write(int slot, MeshSlot mesh) {
        RenderSystem.assertOnRenderThread();
        scratch.clear();
        scratch.putInt((int) mesh.key())
                .putInt((int) (mesh.key() >>> KEY_SHIFT))
                .putInt(mesh.baseQuad())
                .putInt(mesh.quads());
        scratch.flip();

        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(buffer.slice((long) slot * BYTES, BYTES), scratch);
    }

    @Override
    public void close() {
        buffer.close();
    }
}
