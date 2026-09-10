package com.eminus.mesh;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.core.Direction;

public final class ObjWriter {
    private static final Direction[] FACES = Direction.values();
    private static final String[] GROUP_NAMES = {
        "down", "up", "north", "south", "west", "east", "double-sided", "translucent"};

    private static final int CORNERS = 4;

    public static void write(CellMesh mesh, Path file) throws IOException {
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
                    vertex = quad(out, mesh.quad(index), vertex);
                }
            }
        }
    }

    private static int quad(BufferedWriter out, long quad, int vertex) throws IOException {
        Direction face = FACES[Quad.face(quad)];
        int x = Quad.x(quad);
        int y = Quad.y(quad);
        int z = Quad.z(quad);
        int width = Quad.width(quad);
        int height = Quad.height(quad);
        int offset = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;

        switch (face.getAxis()) {
            case X -> {
                int at = x + offset;
                corner(out, at, y, z);
                corner(out, at, y, z + width);
                corner(out, at, y + height, z + width);
                corner(out, at, y + height, z);
            }
            case Y -> {
                int at = y + offset;
                corner(out, x, at, z);
                corner(out, x + width, at, z);
                corner(out, x + width, at, z + height);
                corner(out, x, at, z + height);
            }
            case Z -> {
                int at = z + offset;
                corner(out, x, y, at);
                corner(out, x + width, y, at);
                corner(out, x + width, y + height, at);
                corner(out, x, y + height, at);
            }
        }

        out.write("f " + vertex + " " + (vertex + 1) + " " + (vertex + 2) + " " + (vertex + 3));
        out.newLine();
        return vertex + CORNERS;
    }

    private static void corner(BufferedWriter out, int x, int y, int z) throws IOException {
        out.write("v " + x + " " + y + " " + z);
        out.newLine();
    }

    private ObjWriter() {
    }
}
