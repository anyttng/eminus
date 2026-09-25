package com.eminus.client.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.Set;

import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.model.BakedModel;

import net.minecraft.core.Direction;

public final class ModelRecords implements AutoCloseable {
    public static final int TEXELS = 7;
    public static final int BYTES = TEXELS * 4 * Float.BYTES;
    public static final Format TEXEL_FORMAT = Format.RGBA32_FLOAT;

    private static final String LABEL = "eminus-model-records";
    private static final Set<BufferUsage> USAGE = EnumSet.of(BufferUsage.TEXEL, BufferUsage.COPY_DST);

    private final Gpu gpu;
    private final Buffer buffer;
    private final TexelView texels;
    private final ByteBuffer scratch = ByteBuffer.allocateDirect(BYTES).order(ByteOrder.nativeOrder());

    private ModelRecords(Gpu gpu, Buffer buffer) {
        this.gpu = gpu;
        this.buffer = buffer;
        texels = gpu.texelView(buffer, TEXEL_FORMAT);
    }

    public static ModelRecords create(Gpu gpu, int capacity) {
        gpu.assertRenderThread();
        return new ModelRecords(gpu, gpu.buffer(LABEL, USAGE, (long) capacity * BYTES));
    }

    public TexelView texels() {
        return texels;
    }

    public void write(int modelId, BakedModel model, int variantStart) {
        gpu.assertRenderThread();
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
                .putFloat(Float.intBitsToFloat(model.tintRow()))
                .putFloat(variantStart)
                .putFloat(model.variantCount());
        for (float slope : model.slopes()) {
            scratch.putFloat(slope);
        }
        scratch.flip();

        gpu.write(buffer, (long) modelId * BYTES, scratch);
    }

    @Override
    public void close() {
        texels.close();
        buffer.close();
    }
}
