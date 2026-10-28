package com.eminus.client.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.client.model.game.SpriteMips;
import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.texture.Texture;
import com.eminus.gpu.texture.TextureUsage;
import com.eminus.model.BakedModel;
import com.eminus.model.FaceMips;
import com.eminus.model.Mips;
import com.eminus.model.ModelMetadata;
import com.eminus.model.ModelSource;
import com.eminus.model.Solidify;

public final class ModelAtlas implements AutoCloseable {
    private static final String COLOR_LABEL = "eminus-model-atlas";
    private static final String TINT_MASK_LABEL = "eminus-model-tint-mask";
    private static final Format COLOR_FORMAT = Format.RGBA8_UNORM;
    private static final Format TINT_MASK_FORMAT = Format.R8_UNORM;
    private static final Set<TextureUsage> USAGE = EnumSet.of(TextureUsage.COPY_DST, TextureUsage.SAMPLED);
    private static final int BYTES_PER_TEXEL = 4;
    private static final int TINT_MASK_BYTES_PER_TEXEL = 1;
    private static final int STAGING_ALIGNMENT = BYTES_PER_TEXEL;
    private static final int GROWTH = 2;
    private static final int FACE_LEVELS = Mips.levelCount(BakedModel.FACE_SIDE);
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int RGB_MASK = 0x00FF_FFFF;

    private final Gpu gpu;
    private final int[] faceColor = new int[BakedModel.FACE_TEXELS];
    private final int[] faceTint = new int[BakedModel.FACE_TEXELS];
    private final ByteBuffer colorScratch =
            ByteBuffer.allocateDirect(BakedModel.FACE_TEXELS * BYTES_PER_TEXEL).order(ByteOrder.nativeOrder());
    private final ByteBuffer tintScratch =
            ByteBuffer.allocateDirect(BakedModel.FACE_TEXELS).order(ByteOrder.nativeOrder());

    private final int firstMip;
    private final int cellSide;

    private Texture color;
    private Texture tintMask;
    private int cellsPerSide;

    private ModelAtlas(Gpu gpu, int firstMip, Texture color, Texture tintMask, int cellsPerSide) {
        this.gpu = gpu;
        this.firstMip = firstMip;
        this.cellSide = BakedModel.FACE_SIDE >> firstMip;
        this.color = color;
        this.tintMask = tintMask;
        this.cellsPerSide = cellsPerSide;
    }

    public static ModelAtlas create(Gpu gpu, int cellsPerSide, int firstMip) {
        gpu.assertRenderThread();
        if (firstMip < 0 || firstMip >= FACE_LEVELS) {
            throw new IllegalArgumentException("The model atlas cannot start from mip " + firstMip + " of a "
                    + BakedModel.FACE_SIDE + "-texel face.");
        }

        int cellSide = BakedModel.FACE_SIDE >> firstMip;
        return new ModelAtlas(gpu, firstMip, allocate(gpu, COLOR_LABEL, COLOR_FORMAT, cellsPerSide, cellSide),
                allocate(gpu, TINT_MASK_LABEL, TINT_MASK_FORMAT, cellsPerSide, cellSide), cellsPerSide);
    }

    public Texture color() {
        return color;
    }

    public Texture tintMask() {
        return tintMask;
    }

    public int cellsPerSide() {
        return cellsPerSide;
    }

    public int firstMip() {
        return firstMip;
    }

    public int cellSide() {
        return cellSide;
    }

    public int meanLevel() {
        return Mips.levelCount(cellSide) - 1;
    }

    public int side() {
        return cellsPerSide * cellSide;
    }

    public long bytes() {
        long bytes = 0;
        for (int level = 0; level < Mips.levelCount(cellSide); level++) {
            long levelSide = side() >> level;
            bytes += levelSide * levelSide * (BYTES_PER_TEXEL + TINT_MASK_BYTES_PER_TEXEL);
        }

        return bytes;
    }

    public boolean fits(int modelId) {
        return (long) (modelId + 1) * BakedModel.FACE_COUNT <= (long) cellsPerSide * cellsPerSide;
    }

    public void upload(int modelId, BakedModel model) {
        gpu.assertRenderThread();
        int slot = modelId * BakedModel.FACE_COUNT;

        for (int index = 0; index < BakedModel.FACE_COUNT; index++) {
            writeFace(model, index, slot + index);
        }
    }

    public int grow(ModelSource source) {
        gpu.assertRenderThread();
        int grown = cellsPerSide * GROWTH;
        int maxSide = gpu.maxTextureSide(COLOR_FORMAT);
        if (grown * cellSide > maxSide) {
            Eminus.LOGGER.error("The model atlas cannot grow past {} texels a side, the device limit.", maxSide);
            return 0;
        }

        release();
        color = allocate(gpu, COLOR_LABEL, COLOR_FORMAT, grown, cellSide);
        tintMask = allocate(gpu, TINT_MASK_LABEL, TINT_MASK_FORMAT, grown, cellSide);
        cellsPerSide = grown;
        return reupload(source);
    }

    private int reupload(ModelSource source) {
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

    private static Texture allocate(Gpu gpu, String label, Format format, int cellsPerSide, int cellSide) {
        return gpu.texture(label, USAGE, format, cellsPerSide * cellSide, cellsPerSide * cellSide,
                Mips.levelCount(cellSide));
    }

    private void release() {
        color.close();
        tintMask.close();
    }

    private void writeFace(BakedModel model, int index, int slot) {
        copyFace(model, index);
        int[][] colorLevels = FaceMips.colorLevels(faceColor, BakedModel.FACE_SIDE, model.mips()[index],
                ModelMetadata.has(model.metadata(), ModelMetadata.OPAQUE), SpriteMips::blend);
        int[][] tintLevels = Mips.maskChain(faceTint, BakedModel.FACE_SIDE);
        writeLevels(colorLevels, tintLevels, slot);
    }

    private void copyFace(BakedModel model, int index) {
        System.arraycopy(model.faces(), index * BakedModel.FACE_TEXELS, faceColor, 0, BakedModel.FACE_TEXELS);
        for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
            faceTint[texel] = (faceColor[texel] & ALPHA_MASK) | (model.tinted(index, texel) ? RGB_MASK : 0);
        }

        Solidify.apply(faceTint, BakedModel.FACE_SIDE, BakedModel.FACE_SIDE);
    }

    private void writeLevels(int[][] colorLevels, int[][] tintLevels, int slot) {
        int cellX = slot % cellsPerSide;
        int cellY = slot / cellsPerSide;
        for (int level = firstMip; level < colorLevels.length; level++) {
            int side = BakedModel.FACE_SIDE >> level;
            fillColor(colorLevels[level]);
            fillTint(tintLevels[level]);
            gpu.write(color, level - firstMip, cellX * side, cellY * side, side, side, colorScratch);
            gpu.write(tintMask, level - firstMip, cellX * side, cellY * side, side, side, tintScratch);
        }
    }

    private void fillColor(int[] texels) {
        colorScratch.clear();
        for (int argb : texels) {
            colorScratch.put((byte) (argb >>> 16))
                    .put((byte) (argb >>> 8))
                    .put((byte) argb)
                    .put((byte) (argb >>> 24));
        }

        colorScratch.flip();
    }

    private void fillTint(int[] texels) {
        tintScratch.clear();
        for (int argb : texels) {
            tintScratch.put((byte) (argb >>> 16));
        }

        // Vulkan stages each write in one buffer aligned to a byte: an odd tail leaves the next RGBA8 copy misaligned.
        while (tintScratch.position() % STAGING_ALIGNMENT != 0) {
            tintScratch.put((byte) 0);
        }

        tintScratch.flip();
    }
}
