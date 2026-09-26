package com.eminus.client.render.far;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Set;

import com.eminus.client.frame.FaceShade;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.Std140;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.handoff.NearSections;
import com.eminus.render.far.CameraOrigin;

import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

public final class FarFrame implements AutoCloseable {
    public static final int SIZE = Std140.size()
            .putMat4f().putInt().putInt()
            .putInt().putInt().putIVec3()
            .putFloat().putFloat().putFloat().putFloat().putFloat().putFloat()
            .putIVec3().putVec3()
            .get();

    private static final String LABEL = "eminus-far-frame";
    private static final Set<BufferUsage> USAGE = EnumSet.of(BufferUsage.UNIFORM, BufferUsage.COPY_DST);
    private static final long START_OF_BUFFER = 0L;

    private final Gpu gpu;
    private final Buffer buffer;

    private FarFrame(Gpu gpu, Buffer buffer) {
        this.gpu = gpu;
        this.buffer = buffer;
    }

    public static FarFrame create(Gpu gpu) {
        gpu.assertRenderThread();
        return new FarFrame(gpu, gpu.buffer(LABEL, USAGE, SIZE));
    }

    public Buffer buffer() {
        return buffer;
    }

    public void write(Matrix4fc viewProjection, int minBlockY, int atlasCells, NearSections near,
            FaceShade shade, CameraOrigin camera) {
        gpu.assertRenderThread();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            gpu.write(buffer, START_OF_BUFFER, layout(Std140.into(stack.malloc(SIZE)), viewProjection, minBlockY,
                    atlasCells, near, shade, camera));
        }
    }

    static ByteBuffer layout(Std140 builder, Matrix4fc viewProjection, int minBlockY, int atlasCells,
            NearSections near, FaceShade shade, CameraOrigin camera) {
        return builder
                .putMat4f(viewProjection)
                .putInt(minBlockY)
                .putInt(atlasCells)
                .putInt(near.side())
                .putInt(near.height())
                .putIVec3(near.originBlockX(), near.originBlockY(), near.originBlockZ())
                .putFloat(shade.down())
                .putFloat(shade.up())
                .putFloat(shade.north())
                .putFloat(shade.south())
                .putFloat(shade.west())
                .putFloat(shade.east())
                .putIVec3(camera.blockX(), camera.blockY(), camera.blockZ())
                .putVec3(camera.offsetX(), camera.offsetY(), camera.offsetZ())
                .get();
    }

    @Override
    public void close() {
        buffer.close();
    }
}
