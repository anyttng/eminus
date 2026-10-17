package com.eminus.mesh;

import com.eminus.model.BiomeColors;

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
        if (row == BiomeColors.NO_ROW && placement == QuadPlacement.NONE) {
            return MeshBuffer.UNTINTED;
        }

        return scratch.buffer().colorIndex(color(scratch, row, x, y, z), placement);
    }

    private static int color(MeshScratch scratch, int row, int x, int y, int z) {
        int color = row == BiomeColors.NO_ROW ? TintBlend.NO_COLOR : scratch.blend().color(row, x, y, z);
        return color == TintBlend.NO_COLOR ? MeshBuffer.WHITE : color;
    }

    private QuadTint() {
    }
}
