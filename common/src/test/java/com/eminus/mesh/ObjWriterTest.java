package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.eminus.cell.CellKey;
import com.eminus.cell.FaceMask;
import com.eminus.cell.OccupancyMask;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObjWriterTest {
    private static final int STONE = 1;
    private static final int WATER = 2;
    private static final int COARSE_LEVEL = 2;
    private static final int FINEST_LEVEL = 0;
    private static final int LOW_GAP = 1;
    private static final int HIGH_GAP = 2;
    private static final int GAPS = HIGH_GAP << VoxelEntry.HIGH_GAP_SHIFT | LOW_GAP << VoxelEntry.LOW_GAP_SHIFT;
    private static final int NORTH_WEST = 204;
    private static final int NORTH_EAST = 102;
    private static final int SOUTH_WEST = 51;
    private static final int SOUTH_EAST = 153;
    private static final int CORNERS = NORTH_WEST << FluidCorners.NORTH_WEST_SHIFT
            | NORTH_EAST << FluidCorners.NORTH_EAST_SHIFT | SOUTH_WEST << FluidCorners.SOUTH_WEST_SHIFT
            | SOUTH_EAST << FluidCorners.SOUTH_EAST_SHIFT;
    private static final float TOLERANCE = 1.0E-5F;

    @TempDir
    Path folder;

    private final FakeModels models = new FakeModels();

    ObjWriterTest() {
        models.describe(STONE, ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0));
        models.describe(WATER, ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.ALL, 0,
                ModelMetadata.FLUID | ModelMetadata.TRANSLUCENT));
    }

    @Test
    void aGappedCoarseUpFaceSitsUnderItsHighGap() throws IOException {
        List<float[]> corners = written(COARSE_LEVEL, Direction.UP, STONE, GAPS, 3, 5, 7);

        assertCorners(corners, new float[][] {{3, 5.5F, 7}, {4, 5.5F, 7}, {4, 5.5F, 8}, {3, 5.5F, 8}});
    }

    @Test
    void aGappedCoarseSideFaceSpansOnlyTheOccupiedBlocks() throws IOException {
        List<float[]> corners = written(COARSE_LEVEL, Direction.NORTH, STONE, GAPS, 3, 5, 7);

        assertCorners(corners, new float[][] {{3, 5.25F, 7}, {4, 5.25F, 7}, {4, 5.5F, 7}, {3, 5.5F, 7}});
    }

    @Test
    void aSlopedWaterTopTakesEachCornerHeight() throws IOException {
        List<float[]> corners = written(FINEST_LEVEL, Direction.UP, WATER, CORNERS, 4, 6, 8);

        assertCorners(corners, new float[][] {
            {4, 6 + height(NORTH_WEST), 8}, {5, 6 + height(NORTH_EAST), 8}, {5, 6 + height(SOUTH_EAST), 9},
            {4, 6 + height(SOUTH_WEST), 9}});
    }

    @Test
    void aSlopedWaterSideEndsOnItsTwoCorners() throws IOException {
        List<float[]> corners = written(FINEST_LEVEL, Direction.NORTH, WATER, CORNERS, 4, 6, 8);

        assertCorners(corners, new float[][] {
            {4, 6, 8}, {5, 6, 8}, {5, 6 + height(NORTH_EAST), 8}, {4, 6 + height(NORTH_WEST), 8}});
    }

    private List<float[]> written(int level, Direction face, int modelId, int placement, int x, int y, int z)
            throws IOException {
        MeshBuffer buffer = new MeshBuffer();
        long data = Quad.data(0, modelId, buffer.colourIndex(MeshBuffer.WHITE, placement));
        buffer.add(face.get3DDataValue(), Quad.of(data, face.get3DDataValue(), x, y, z, 1, 1));
        CellMesh mesh = buffer.freeze(CellKey.pack(level, 0, 0, 0), OccupancyMask.EMPTY);
        Path file = folder.resolve("cell.obj");

        ObjWriter.write(mesh, models, file);

        List<float[]> corners = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("v ")) {
                String[] parts = line.split(" ", -1);
                corners.add(new float[] {
                    Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
            }
        }

        return corners;
    }

    private static float height(int steps) {
        return steps / (float) FluidCorners.STEPS;
    }

    private static void assertCorners(List<float[]> corners, float[][] expected) {
        assertEquals(expected.length, corners.size());
        for (int corner = 0; corner < expected.length; corner++) {
            for (int axis = 0; axis < expected[corner].length; axis++) {
                assertEquals(expected[corner][axis], corners.get(corner)[axis], TOLERANCE,
                        "corner " + corner + " axis " + axis);
            }
        }
    }
}
