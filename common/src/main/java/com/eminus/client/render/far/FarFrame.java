package com.eminus.client.render.far;

import java.nio.ByteBuffer;

import com.eminus.handoff.NearSections;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

public final class FarFrame implements AutoCloseable {
    public static final int SIZE = new Std140SizeCalculator()
            .putMat4f().putInt().putInt()
            .putInt().putInt().putIVec3()
            .get();

    private static final String LABEL = "eminus-far-frame";
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;

    private final GpuBuffer buffer;

    private FarFrame(GpuBuffer buffer) {
        this.buffer = buffer;
    }

    public static FarFrame create() {
        RenderSystem.assertOnRenderThread();
        return new FarFrame(RenderSystem.getDevice().createBuffer(() -> LABEL, USAGE, SIZE));
    }

    public GpuBuffer buffer() {
        return buffer;
    }

    public void write(Matrix4fc viewProjection, int minBlockY, int atlasCells, NearSections near) {
        RenderSystem.assertOnRenderThread();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = Std140Builder.onStack(stack, SIZE)
                    .putMat4f(viewProjection)
                    .putInt(minBlockY)
                    .putInt(atlasCells)
                    .putInt(near.side())
                    .putInt(near.height())
                    .putIVec3(near.originBlockX(), near.originBlockY(), near.originBlockZ())
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), written);
        }
    }

    @Override
    public void close() {
        buffer.close();
    }
}
