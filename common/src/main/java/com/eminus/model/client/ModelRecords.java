package com.eminus.model.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.eminus.model.BakedModel;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.core.Direction;

public final class ModelRecords implements AutoCloseable {
    public static final int TEXELS = 4;
    public static final int BYTES = TEXELS * 4 * Float.BYTES;

    private static final String LABEL = "eminus-model-records";
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST;
    private static final float PADDING = 0.0F;

    private final GpuBuffer buffer;
    private final ByteBuffer scratch = ByteBuffer.allocateDirect(BYTES).order(ByteOrder.nativeOrder());

    private ModelRecords(GpuBuffer buffer) {
        this.buffer = buffer;
    }

    public static ModelRecords create(int capacity) {
        RenderSystem.assertOnRenderThread();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> LABEL, USAGE, (long) capacity * BYTES);
        return new ModelRecords(buffer);
    }

    public GpuBuffer buffer() {
        return buffer;
    }

    public void write(int modelId, BakedModel model, int tintRow) {
        RenderSystem.assertOnRenderThread();
        float[] insets = model.insets();
        float[] bounds = model.bounds();

        scratch.clear();
        scratch.putFloat(insets[Direction.DOWN.ordinal()])
                .putFloat(insets[Direction.UP.ordinal()])
                .putFloat(insets[Direction.NORTH.ordinal()])
                .putFloat(insets[Direction.SOUTH.ordinal()])
                .putFloat(insets[Direction.WEST.ordinal()])
                .putFloat(insets[Direction.EAST.ordinal()])
                .putFloat(bounds[BakedModel.MIN_X])
                .putFloat(bounds[BakedModel.MIN_Y])
                .putFloat(bounds[BakedModel.MIN_Z])
                .putFloat(bounds[BakedModel.MAX_X])
                .putFloat(bounds[BakedModel.MAX_Y])
                .putFloat(bounds[BakedModel.MAX_Z])
                .putFloat(Float.intBitsToFloat(model.metadata()))
                .putFloat(Float.intBitsToFloat(tintRow))
                .putFloat(PADDING)
                .putFloat(PADDING);
        scratch.flip();

        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(buffer.slice((long) modelId * BYTES, BYTES), scratch);
    }

    @Override
    public void close() {
        buffer.close();
    }
}
