package com.eminus.model.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.eminus.Eminus;
import com.eminus.model.BakedModel;
import com.eminus.model.Mips;
import com.eminus.model.ModelSource;
import com.eminus.model.Solidify;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

public final class ModelAtlas implements AutoCloseable {
    private static final String LABEL = "eminus-model-atlas";
    private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
    private static final int LAYERS = 1;
    private static final int BYTES_PER_TEXEL = 4;
    private static final int GROWTH = 2;

    private final int[] face = new int[BakedModel.FACE_TEXELS];
    private final ByteBuffer scratch =
            ByteBuffer.allocateDirect(BakedModel.FACE_TEXELS * BYTES_PER_TEXEL).order(ByteOrder.nativeOrder());

    private GpuTexture texture;
    private int cellsPerSide;

    private ModelAtlas(GpuTexture texture, int cellsPerSide) {
        this.texture = texture;
        this.cellsPerSide = cellsPerSide;
    }

    public static ModelAtlas create(int cellsPerSide) {
        RenderSystem.assertOnRenderThread();
        return new ModelAtlas(allocate(cellsPerSide), cellsPerSide);
    }

    public GpuTexture texture() {
        return texture;
    }

    public int cellsPerSide() {
        return cellsPerSide;
    }

    public int side() {
        return cellsPerSide * BakedModel.FACE_SIDE;
    }

    public boolean fits(int modelId) {
        return (long) (modelId + 1) * BakedModel.FACE_COUNT <= (long) cellsPerSide * cellsPerSide;
    }

    public void upload(int modelId, BakedModel model) {
        RenderSystem.assertOnRenderThread();
        int slot = modelId * BakedModel.FACE_COUNT;

        for (int index = 0; index < BakedModel.FACE_COUNT; index++) {
            writeFace(model, index, slot + index);
        }
    }

    public int grow(ModelSource source) {
        RenderSystem.assertOnRenderThread();
        int grown = cellsPerSide * GROWTH;
        if (grown * BakedModel.FACE_SIDE > maxSide()) {
            Eminus.LOGGER.error("The model atlas cannot grow past {} texels a side, the device limit.", maxSide());
            return 0;
        }

        texture.close();
        texture = allocate(grown);
        cellsPerSide = grown;

        int reuploaded = 0;
        for (int modelId = 0; modelId < source.modelCount(); modelId++) {
            if (!fits(modelId)) {
                break;
            }

            upload(modelId, source.model(modelId));
            reuploaded++;
        }

        return reuploaded;
    }

    @Override
    public void close() {
        texture.close();
    }

    private static GpuTexture allocate(int cellsPerSide) {
        return RenderSystem.getDevice().createTexture(LABEL, USAGE, GpuFormat.RGBA8_UNORM,
                cellsPerSide * BakedModel.FACE_SIDE, cellsPerSide * BakedModel.FACE_SIDE, LAYERS,
                Mips.levelCount(BakedModel.FACE_SIDE));
    }

    private static int maxSide() {
        return RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSizeForFormat(GpuFormat.RGBA8_UNORM);
    }

    private void writeFace(BakedModel model, int index, int slot) {
        System.arraycopy(model.faces(), index * BakedModel.FACE_TEXELS, face, 0, BakedModel.FACE_TEXELS);
        Solidify.apply(face, BakedModel.FACE_SIDE, BakedModel.FACE_SIDE);

        int[][] levels = Mips.chain(face, BakedModel.FACE_SIDE);
        int cellX = slot % cellsPerSide;
        int cellY = slot / cellsPerSide;

        for (int level = 0; level < levels.length; level++) {
            int side = BakedModel.FACE_SIDE >> level;
            fill(levels[level]);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                    texture, scratch, level, 0, cellX * side, cellY * side, side, side);
        }
    }

    private void fill(int[] texels) {
        scratch.clear();
        for (int argb : texels) {
            scratch.put((byte) (argb >>> 16))
                    .put((byte) (argb >>> 8))
                    .put((byte) argb)
                    .put((byte) (argb >>> 24));
        }

        scratch.flip();
    }
}
