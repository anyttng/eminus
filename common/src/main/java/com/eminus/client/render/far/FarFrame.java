package com.eminus.client.render.far;

import java.nio.ByteBuffer;

import com.eminus.handoff.NearSections;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.world.level.CardinalLighting;

import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

public final class FarFrame implements AutoCloseable {
    static final int IVEC3_ALIGNMENT = 16;

    public static final int SIZE = new Std140SizeCalculator()
            .putMat4f().putInt().putInt()
            .putInt().putInt()
            .align(IVEC3_ALIGNMENT).putInt().putInt().putInt()
            .putFloat().putFloat().putFloat().putFloat().putFloat().putFloat()
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

    public void write(Matrix4fc viewProjection, int minBlockY, int atlasCells, NearSections near,
            CardinalLighting shade) {
        RenderSystem.assertOnRenderThread();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = layout(Std140Builder.onStack(stack, SIZE), viewProjection, minBlockY, atlasCells,
                    near, shade);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), written);
        }
    }

    static ByteBuffer layout(Std140Builder builder, Matrix4fc viewProjection, int minBlockY, int atlasCells,
            NearSections near, CardinalLighting shade) {
        return builder
                .putMat4f(viewProjection)
                .putInt(minBlockY)
                .putInt(atlasCells)
                .putInt(near.side())
                .putInt(near.height())
                .align(IVEC3_ALIGNMENT)
                .putInt(near.originBlockX())
                .putInt(near.originBlockY())
                .putInt(near.originBlockZ())
                .putFloat(shade.down())
                .putFloat(shade.up())
                .putFloat(shade.north())
                .putFloat(shade.south())
                .putFloat(shade.west())
                .putFloat(shade.east())
                .get();
    }

    @Override
    public void close() {
        buffer.close();
    }
}
