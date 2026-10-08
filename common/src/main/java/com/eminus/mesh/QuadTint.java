package com.eminus.mesh;

import com.eminus.model.BiomeColours;

public final class QuadTint {
    public static int of(MeshScratch scratch, MeshModels models, long entry, int modelId, int x, int y, int z) {
        return entry(scratch, models, scratch.placements().block(models, entry, x, y, z), modelId, x, y, z);
    }

    public static int ofFluid(MeshScratch scratch, MeshModels models, long entry, int modelId, int corners, int x,
            int y, int z) {
        return entry(scratch, models, scratch.placements().fluid(entry, corners), modelId, x, y, z);
    }

    private static int entry(MeshScratch scratch, MeshModels models, int placement, int modelId, int x, int y,
            int z) {
        int row = models.tintRow(modelId);
        if (row == BiomeColours.NO_ROW && placement == QuadPlacement.NONE) {
            return MeshBuffer.UNTINTED;
        }

        return scratch.buffer().colourIndex(colour(scratch, row, x, y, z), placement);
    }

    private static int colour(MeshScratch scratch, int row, int x, int y, int z) {
        int colour = row == BiomeColours.NO_ROW ? TintBlend.NO_COLOUR : scratch.blend().colour(row, x, y, z);
        return colour == TintBlend.NO_COLOUR ? MeshBuffer.WHITE : colour;
    }

    private QuadTint() {
    }
}
