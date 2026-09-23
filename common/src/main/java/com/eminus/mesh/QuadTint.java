package com.eminus.mesh;

import com.eminus.cell.VoxelEntry;
import com.eminus.model.BiomeColours;

public final class QuadTint {
    public static int of(MeshScratch scratch, MeshModels models, long entry, int modelId, int x, int y, int z) {
        int offset = scratch.offsets().at(models, entry, x, y, z);
        int row = models.tintRow(modelId);
        if (row == BiomeColours.NO_ROW && offset == QuadOffset.NONE) {
            return MeshBuffer.UNTINTED;
        }

        return scratch.buffer().colourIndex(colour(scratch, row, x, y, z), offset);
    }

    public static int ofFluid(MeshScratch scratch, MeshModels models, long entry, int modelId, int corners, int x,
            int y, int z) {
        int row = models.tintRow(modelId);
        int gaps = VoxelEntry.gaps(entry);
        if (row == BiomeColours.NO_ROW && corners == FluidCorners.FLAT && gaps == VoxelEntry.NO_GAPS) {
            return MeshBuffer.UNTINTED;
        }

        int colour = colour(scratch, row, x, y, z);
        return corners == FluidCorners.FLAT
                ? scratch.buffer().colourIndex(colour, gaps)
                : scratch.buffer().cornerIndex(colour, corners);
    }

    private static int colour(MeshScratch scratch, int row, int x, int y, int z) {
        int colour = row == BiomeColours.NO_ROW ? TintBlend.NO_COLOUR : scratch.blend().colour(row, x, y, z);
        return colour == TintBlend.NO_COLOUR ? MeshBuffer.WHITE : colour;
    }

    private QuadTint() {
    }
}
