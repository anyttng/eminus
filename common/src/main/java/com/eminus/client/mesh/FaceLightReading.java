package com.eminus.client.mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.client.model.ClientBakery;
import com.eminus.client.session.ClientSession;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.Quad;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class FaceLightReading {
    public static final int LEVEL = 0;
    public static final int CELL_X = 1;
    public static final int CELL_Y = 2;
    public static final int CELL_Z = 3;
    public static final int UP_QUADS = 4;
    public static final int UP_BLOCK_LIT = 5;
    public static final int UP_MAX_BLOCK_LIGHT = 6;
    public static final int SIDE_QUADS = 7;
    public static final int SIDE_BLOCK_LIT = 8;
    public static final int WIDTH = 9;

    private static final int TIMEOUT_SECONDS = 120;
    private static final int BLOCK_LIGHT_MASK = 0xF;

    public static @Nullable CompletableFuture<List<int[]>> start(int level, int minX, int minY, int minZ, int maxX,
            int maxY, int maxZ) {
        DimensionRuntime runtime = ClientSession.runtime();
        EminusInstance instance = ClientSession.instance();
        if (runtime == null || instance == null) {
            return null;
        }

        CellFrame frame = runtime.frame();
        long[] keys = keys(frame, level, minX, minY, minZ, maxX, maxY, maxZ);
        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        ClientBakery baking = ClientBakery.start(Minecraft.getInstance());

        return CellMeshing.mesh(runtime, instance, baking, keys, TIMEOUT_SECONDS)
                .thenApply(meshes -> rows(frame, keys, meshes, box))
                .whenComplete((rows, failure) -> baking.stop());
    }

    private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean overlaps(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
            return fromX <= maxX && toX >= minX && fromY <= maxY && toY >= minY && fromZ <= maxZ && toZ >= minZ;
        }
    }

    private static long[] keys(CellFrame frame, int level, int minX, int minY, int minZ, int maxX, int maxY,
            int maxZ) {
        List<Long> keys = new ArrayList<>();
        for (int cellX = frame.cellX(minX, level); cellX <= frame.cellX(maxX, level); cellX++) {
            for (int cellY = frame.cellY(minY, level); cellY <= frame.cellY(maxY, level); cellY++) {
                for (int cellZ = frame.cellZ(minZ, level); cellZ <= frame.cellZ(maxZ, level); cellZ++) {
                    keys.add(CellKey.pack(level, cellX, cellY, cellZ));
                }
            }
        }

        return keys.stream().mapToLong(Long::longValue).toArray();
    }

    private static List<int[]> rows(CellFrame frame, long[] keys, Map<Long, CellMesh> meshes, Box box) {
        List<int[]> rows = new ArrayList<>(keys.length);
        for (long key : keys) {
            rows.add(row(frame, key, meshes.get(key), box));
        }

        return rows;
    }

    private static int[] row(CellFrame frame, long key, CellMesh mesh, Box box) {
        int level = CellKey.level(key);
        int voxelBlocks = DetailLevel.blocksPerVoxel(level);
        int[] row = new int[WIDTH];
        row[LEVEL] = level;
        row[CELL_X] = CellKey.x(key);
        row[CELL_Y] = CellKey.y(key);
        row[CELL_Z] = CellKey.z(key);

        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            if (Quad.isBlade(quad)) {
                continue;
            }

            Direction face = Direction.from3DDataValue(Quad.face(quad));
            int spanX = switch (face.getAxis()) {
                case X -> 1;
                case Y, Z -> Quad.width(quad);
            };
            int spanY = switch (face.getAxis()) {
                case X, Z -> Quad.height(quad);
                case Y -> 1;
            };
            int spanZ = switch (face.getAxis()) {
                case X -> Quad.width(quad);
                case Y -> Quad.height(quad);
                case Z -> 1;
            };
            int fromX = frame.blockXOf(key, Quad.x(quad));
            int fromY = frame.blockYOf(key, Quad.y(quad));
            int fromZ = frame.blockZOf(key, Quad.z(quad));
            if (!box.overlaps(fromX, fromY, fromZ, fromX + spanX * voxelBlocks - 1,
                    fromY + spanY * voxelBlocks - 1, fromZ + spanZ * voxelBlocks - 1)) {
                continue;
            }

            int blockLight = Quad.light(quad) & BLOCK_LIGHT_MASK;
            if (face == Direction.UP) {
                row[UP_QUADS]++;
                row[UP_MAX_BLOCK_LIGHT] = Math.max(row[UP_MAX_BLOCK_LIGHT], blockLight);
                if (blockLight > 0) {
                    row[UP_BLOCK_LIT]++;
                }
            } else if (face.getAxis().isHorizontal()) {
                row[SIDE_QUADS]++;
                if (blockLight > 0) {
                    row[SIDE_BLOCK_LIT]++;
                }
            }
        }

        return row;
    }

    private FaceLightReading() {
    }
}
