package com.eminus.mesh;

import com.eminus.model.BiomeColours;

public final class QuadTint {
    public static int of(MeshScratch scratch, MeshModels models, int stateId, int modelId, int x, int y, int z) {
        int offset = scratch.offsets().at(models, stateId, x, y, z);
        int row = models.tintRow(modelId);
        if (row == BiomeColours.NO_ROW && offset == QuadOffset.NONE) {
            return MeshBuffer.UNTINTED;
        }

        int colour = row == BiomeColours.NO_ROW ? TintBlend.NO_COLOUR : scratch.blend().colour(row, x, y, z);
        return scratch.buffer().colourIndex(colour == TintBlend.NO_COLOUR ? MeshBuffer.WHITE : colour, offset);
    }

    private QuadTint() {
    }
}
