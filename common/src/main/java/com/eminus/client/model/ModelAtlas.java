package com.eminus.client.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.texture.Texture;
import com.eminus.gpu.texture.TextureUsage;
import com.eminus.model.BakedModel;
import com.eminus.model.Mips;
import com.eminus.model.ModelSource;
import com.eminus.model.Solidify;

public final class ModelAtlas implements AutoCloseable {
    private static final String COLOUR_LABEL = "eminus-model-atlas";
    private static final String TINT_MASK_LABEL = "eminus-model-tint-mask";
    private static final Format COLOUR_FORMAT = Format.RGBA8_UNORM;
    private static final Format TINT_MASK_FORMAT = Format.R8_UNORM;
    private static final Set<TextureUsage> USAGE = EnumSet.of(TextureUsage.COPY_DST, TextureUsage.SAMPLED);
    private static final int BYTES_PER_TEXEL = 4;
    private static final int STAGING_ALIGNMENT = BYTES_PER_TEXEL;
    private static final int GROWTH = 2;
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int RGB_MASK = 0x00FF_FFFF;

    private final Gpu gpu;
    private final int[] faceColour = new int[BakedModel.FACE_TEXELS];
    private final int[] faceTint = new int[BakedModel.FACE_TEXELS];
    private final ByteBuffer colourScratch =
            ByteBuffer.allocateDirect(BakedModel.FACE_TEXELS * BYTES_PER_TEXEL).order(ByteOrder.nativeOrder());
    private final ByteBuffer tintScratch =
            ByteBuffer.allocateDirect(BakedModel.FACE_TEXELS).order(ByteOrder.nativeOrder());

    private Texture colour;
    private Texture tintMask;
    private int cellsPerSide;

    private ModelAtlas(Gpu gpu, Texture colour, Texture tintMask, int cellsPerSide) {
        this.gpu = gpu;
        this.colour = colour;
        this.tintMask = tintMask;
        this.cellsPerSide = cellsPerSide;
    }

    public static ModelAtlas create(Gpu gpu, int cellsPerSide) {
        gpu.assertRenderThread();
        return new ModelAtlas(gpu, allocate(gpu, COLOUR_LABEL, COLOUR_FORMAT, cellsPerSide),
                allocate(gpu, TINT_MASK_LABEL, TINT_MASK_FORMAT, cellsPerSide), cellsPerSide);
    }

    public Texture colour() {
        return colour;
    }

    public Texture tintMask() {
        return tintMask;
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
        gpu.assertRenderThread();
        int slot = modelId * BakedModel.FACE_COUNT;

        for (int index = 0; index < BakedModel.FACE_COUNT; index++) {
            writeFace(model, index, slot + index);
        }
    }

    public int grow(ModelSource source) {
        gpu.assertRenderThread();
        int grown = cellsPerSide * GROWTH;
        int maxSide = gpu.maxTextureSide(COLOUR_FORMAT);
        if (grown * BakedModel.FACE_SIDE > maxSide) {
            Eminus.LOGGER.error("The model atlas cannot grow past {} texels a side, the device limit.", maxSide);
            return 0;
        }

        release();
        colour = allocate(gpu, COLOUR_LABEL, COLOUR_FORMAT, grown);
        tintMask = allocate(gpu, TINT_MASK_LABEL, TINT_MASK_FORMAT, grown);
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

    private static Texture allocate(Gpu gpu, String label, Format format, int cellsPerSide) {
        return gpu.texture(label, USAGE, format, cellsPerSide * BakedModel.FACE_SIDE,
                cellsPerSide * BakedModel.FACE_SIDE, Mips.levelCount(BakedModel.FACE_SIDE));
    }

    private void release() {
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

        int[][] colourLevels = Mips.colourChain(faceColour, BakedModel.FACE_SIDE);
        int[][] tintLevels = Mips.maskChain(faceTint, BakedModel.FACE_SIDE);
        int cellX = slot % cellsPerSide;
        int cellY = slot / cellsPerSide;

        for (int level = 0; level < colourLevels.length; level++) {
            int side = BakedModel.FACE_SIDE >> level;
            fillColour(colourLevels[level]);
            fillTint(tintLevels[level]);
            gpu.write(colour, level, cellX * side, cellY * side, side, side, colourScratch);
            gpu.write(tintMask, level, cellX * side, cellY * side, side, side, tintScratch);
        }
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

        // Vulkan stages each write in one buffer aligned to a byte: an odd tail leaves the next RGBA8 copy misaligned.
        while (tintScratch.position() % STAGING_ALIGNMENT != 0) {
            tintScratch.put((byte) 0);
        }

        tintScratch.flip();
    }
}
