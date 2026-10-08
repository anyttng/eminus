package com.eminus.mesh;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

public final class ObjWriter {
    private static final Direction[] FACES = Direction.values();
    private static final String[] GROUP_NAMES = {
        "down", "up", "north", "south", "west", "east", "double-sided", "translucent", "border-down", "border-up",
        "border-north", "border-south", "border-west", "border-east"};
    private static final int[][] CORNER_STEPS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private static final int X = Direction.Axis.X.ordinal();
    private static final int Y = Direction.Axis.Y.ordinal();
    private static final int Z = Direction.Axis.Z.ordinal();

    public static void write(CellMesh mesh, MeshModels models, Path file) throws IOException {
        int level = CellKey.level(mesh.key());

        try (BufferedWriter out = Files.newBufferedWriter(file)) {
            int vertex = 1;

            for (int group = 0; group < QuadGroups.COUNT; group++) {
                int count = mesh.groupCount(group);
                if (count == 0) {
                    continue;
                }

                out.write("g " + GROUP_NAMES[group]);
                out.newLine();
                int start = mesh.groupStart(group);

                for (int index = start; index < start + count; index++) {
                    long quad = mesh.quad(index);
                    boolean fluid = ModelMetadata.has(models.metadata(Quad.modelId(quad)), ModelMetadata.FLUID);
                    vertex = quad(out, quad, new Placed(level, fluid, mesh.placement(quad)), vertex);
                }
            }
        }
    }

    private record Placed(int offset, int corners, float bottom, float top) {
        Placed(int level, boolean fluid, int placement) {
            this(QuadPlacement.offset(level, fluid, placement), QuadPlacement.corners(level, fluid, placement),
                    QuadPlacement.lowGap(level, placement) / (float) DetailLevel.blocksPerVoxel(level),
                    QuadPlacement.highGap(level, placement) / (float) DetailLevel.blocksPerVoxel(level));
        }
    }

    private static int quad(BufferedWriter out, long quad, Placed placed, int vertex) throws IOException {
        float[] origin = {
            Quad.x(quad) + QuadOffset.x(placed.offset()), Quad.y(quad) + QuadOffset.y(placed.offset()),
            Quad.z(quad) + QuadOffset.z(placed.offset())};
        int width = Quad.width(quad);
        int height = Quad.height(quad);

        if (Quad.isBlade(quad)) {
            blade(out, Quad.face(quad) - Quad.FIRST_BLADE_FACE, origin, height, placed);
            return face(out, vertex);
        }

        Direction face = FACES[Quad.face(quad)];
        Direction.Axis normal = face.getAxis();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int across = PlaneAxes.width(normal).ordinal();
        int up = PlaneAxes.height(normal).ordinal();

        for (int[] step : CORNER_STEPS) {
            float[] corner = origin.clone();
            corner[normal.ordinal()] += positive ? 1 : 0;
            corner[across] += step[0] * width;
            corner[up] += step[1] * height;

            boolean onTop = normal == Direction.Axis.Y ? positive : step[1] == 1;
            float rise = normal == Direction.Axis.Y ? 1 : height;
            corner[Y] = onTop ? origin[Y] + rise - placed.top() : origin[Y] + placed.bottom();
            if (onTop && placed.corners() != FluidCorners.FLAT) {
                corner[Y] = origin[Y] + rise - 1 + FluidCorners.height(placed.corners(), corner[X] > origin[X],
                        corner[Z] > origin[Z]);
            }

            corner(out, corner[X], corner[Y], corner[Z]);
        }

        return face(out, vertex);
    }

    private static void blade(BufferedWriter out, int blade, float[] origin, int height, Placed placed)
            throws IOException {
        float near = blade == 0 ? origin[X] : origin[X] + 1;
        float far = blade == 0 ? origin[X] + 1 : origin[X];
        float bottom = origin[Y] + placed.bottom();
        float top = origin[Y] + height - placed.top();

        corner(out, near, bottom, origin[Z]);
        corner(out, far, bottom, origin[Z] + 1);
        corner(out, far, top, origin[Z] + 1);
        corner(out, near, top, origin[Z]);
    }

    private static int face(BufferedWriter out, int vertex) throws IOException {
        out.write("f " + vertex + " " + (vertex + 1) + " " + (vertex + 2) + " " + (vertex + 3));
        out.newLine();
        return vertex + CORNER_STEPS.length;
    }

    private static void corner(BufferedWriter out, float x, float y, float z) throws IOException {
        out.write("v " + x + " " + y + " " + z);
        out.newLine();
    }

    private ObjWriter() {
    }
}
