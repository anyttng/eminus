package com.eminus.client.model.game;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.eminus.model.BakedModel;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.util.FastColor;

public final class ModelSheet {
    public static void write(List<BakedModel> models, Path file) throws IOException {
        if (models.isEmpty()) {
            return;
        }

        int width = BakedModel.FACE_COUNT * BakedModel.FACE_SIDE;
        int height = models.size() * BakedModel.FACE_SIDE;

        try (NativeImage sheet = new NativeImage(width, height, true)) {
            for (int row = 0; row < models.size(); row++) {
                paint(sheet, models.get(row), row * BakedModel.FACE_SIDE);
            }

            sheet.writeToFile(file);
        }
    }

    private static void paint(NativeImage sheet, BakedModel model, int top) {
        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            int left = face * BakedModel.FACE_SIDE;

            for (int y = 0; y < BakedModel.FACE_SIDE; y++) {
                int row = BakedModel.FACE_SIDE - 1 - y;
                for (int x = 0; x < BakedModel.FACE_SIDE; x++) {
                    sheet.setPixelRGBA(left + x, top + y,
                            FastColor.ABGR32.fromArgb32(model.argb(face, row * BakedModel.FACE_SIDE + x)));
                }
            }
        }
    }

    private ModelSheet() {
    }
}
