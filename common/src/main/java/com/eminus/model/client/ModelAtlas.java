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
import com.mojang.blaze3d.textures.GpuTextureView;

public final class ModelAtlas implements AutoCloseable {
    private static final String COLOUR_LABEL = "eminus-model-atlas";
    private static final String TINT_MASK_LABEL = "eminus-model-tint-mask";
    private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
    private static final int LAYERS = 1;
    private static final int BYTES_PER_TEXEL = 4;
    private static final int GROWTH = 2;
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int RGB_MASK = 0x00FF_FFFF;

    private final int[] faceColour = new int[BakedModel.FACE_TEXELS];
    private final int[] faceTint = new int[BakedModel.FACE_TEXELS];
    private final ByteBuffer colourScratch =
            ByteBuffer.allocateDirect(BakedModel.FACE_TEXELS * BYTES_PER_TEXEL).order(ByteOrder.nativeOrder());
    private final ByteBuffer tintScratch =
            ByteBuffer.allocateDirect(BakedModel.FACE_TEXELS).order(ByteOrder.nativeOrder());

    private GpuTexture colour;
    private GpuTexture tintMask;
    private GpuTextureView colourView;
    private GpuTextureView tintMaskView;
    private int cellsPerSide;

    private ModelAtlas(GpuTexture colour, GpuTexture tintMask, int cellsPerSide) {
        this.colour = colour;
        this.tintMask = tintMask;
        this.cellsPerSide = cellsPerSide;
    }

    public static ModelAtlas create(int cellsPerSide) {
        RenderSystem.assertOnRenderThread();
        return new ModelAtlas(allocate(COLOUR_LABEL, GpuFormat.RGBA8_UNORM, cellsPerSide),
                allocate(TINT_MASK_LABEL, GpuFormat.R8_UNORM, cellsPerSide), cellsPerSide);
    }

    public GpuTextureView colourView() {
        RenderSystem.assertOnRenderThread();
        if (colourView == null) {
            colourView = RenderSystem.getDevice().createTextureView(colour);
        }

        return colourView;
    }

    public GpuTextureView tintMaskView() {
        RenderSystem.assertOnRenderThread();
        if (tintMaskView == null) {
            tintMaskView = RenderSystem.getDevice().createTextureView(tintMask);
        }

        return tintMaskView;
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

        release();
        colour = allocate(COLOUR_LABEL, GpuFormat.RGBA8_UNORM, grown);
        tintMask = allocate(TINT_MASK_LABEL, GpuFormat.R8_UNORM, grown);
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
        release();
    }

    private static GpuTexture allocate(String label, GpuFormat format, int cellsPerSide) {
        return RenderSystem.getDevice().createTexture(label, USAGE, format,
                cellsPerSide * BakedModel.FACE_SIDE, cellsPerSide * BakedModel.FACE_SIDE, LAYERS,
                Mips.levelCount(BakedModel.FACE_SIDE));
    }

    private static int maxSide() {
        return RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSizeForFormat(GpuFormat.RGBA8_UNORM);
    }

    private void release() {
        if (colourView != null) {
            colourView.close();
            colourView = null;
        }

        if (tintMaskView != null) {
            tintMaskView.close();
            tintMaskView = null;
        }

        colour.close();
        tintMask.close();
    }

    private void writeFace(BakedModel model, int index, int slot) {
        System.arraycopy(model.faces(), index * BakedModel.FACE_TEXELS, faceColour, 0, BakedModel.FACE_TEXELS);
        for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
            faceTint[texel] = faceColour[texel] & ALPHA_MASK | (model.tinted(index, texel) ? RGB_MASK : 0);
        }

        Solidify.apply(faceColour, BakedModel.FACE_SIDE, BakedModel.FACE_SIDE);
        Solidify.apply(faceTint, BakedModel.FACE_SIDE, BakedModel.FACE_SIDE);

        int[][] colourLevels = Mips.chain(faceColour, BakedModel.FACE_SIDE);
        int[][] tintLevels = Mips.chain(faceTint, BakedModel.FACE_SIDE);
        int cellX = slot % cellsPerSide;
        int cellY = slot / cellsPerSide;

        for (int level = 0; level < colourLevels.length; level++) {
            int side = BakedModel.FACE_SIDE >> level;
            fillColour(colourLevels[level]);
            fillTint(tintLevels[level]);
            write(colour, colourScratch, level, cellX * side, cellY * side, side);
            write(tintMask, tintScratch, level, cellX * side, cellY * side, side);
        }
    }

    private static void write(GpuTexture texture, ByteBuffer source, int level, int x, int y, int side) {
        RenderSystem.getDevice().createCommandEncoder()
                .writeToTexture(texture, source, level, 0, x, y, side, side);
    }

    private void fillColour(int[] texels) {
        colourScratch.clear();
        for (int argb : texels) {
            colourScratch.put((byte) (argb >>> 16))
                    .put((byte) (argb >>> 8))
                    .put((byte) argb)
                    .put((byte) (argb >>> 24));
        }

        colourScratch.flip();
    }

    private void fillTint(int[] texels) {
        tintScratch.clear();
        for (int argb : texels) {
            tintScratch.put((byte) (argb >>> 16));
        }

        tintScratch.flip();
    }
}
