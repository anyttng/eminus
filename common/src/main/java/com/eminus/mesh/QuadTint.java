package com.eminus.mesh;

import com.eminus.model.BiomeColours;

public final class QuadTint {
    public static int of(MeshScratch scratch, MeshModels models, int modelId, int x, int y, int z) {
        int row = models.tintRow(modelId);
        if (row == BiomeColours.NO_ROW) {
            return MeshBuffer.UNTINTED;
        }

        int colour = scratch.blend().colour(row, x, y, z);
        return scratch.buffer().colourIndex(colour == TintBlend.NO_COLOUR ? MeshBuffer.WHITE : colour);
    }

    private QuadTint() {
    }
}
