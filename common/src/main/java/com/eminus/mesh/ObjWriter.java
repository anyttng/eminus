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
        int x = Quad.x(quad);
        int y = Quad.y(quad);
        int z = Quad.z(quad);
        int width = Quad.width(quad);
        int height = Quad.height(quad);

        if (Quad.isBlade(quad)) {
            blade(out, Quad.face(quad) - Quad.FIRST_BLADE_FACE, x, y, z, height);
            return face(out, vertex);
        }

        Direction face = FACES[Quad.face(quad)];
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

        return face(out, vertex);
    }

    private static void blade(BufferedWriter out, int blade, int x, int y, int z, int height) throws IOException {
        int near = blade == 0 ? x : x + 1;
        int far = blade == 0 ? x + 1 : x;

        corner(out, near, y, z);
        corner(out, far, y, z + 1);
        corner(out, far, y + height, z + 1);
        corner(out, near, y + height, z);
    }

    private static int face(BufferedWriter out, int vertex) throws IOException {
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
