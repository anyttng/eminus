package com.eminus.client.mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.eminus.cell.CellFrame;
import com.eminus.cell.DetailLevel;
import com.eminus.client.model.ClientBakery;
import com.eminus.client.session.ClientSession;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.PlaneAxes;
import com.eminus.mesh.Quad;
import com.eminus.mesh.QuadGroups;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class FaceWordReading {
    public static final int BLOCK_X = 0;
    public static final int BLOCK_Y = 1;
    public static final int BLOCK_Z = 2;
    public static final int WIDTH = 3;
    public static final int HEIGHT = 4;
    public static final int WORD = 5;
    public static final int LIGHT = 6;
    public static final int MODEL_ID = 7;
    public static final int GROUP = 8;
    public static final int FIELDS = 9;

    private static final int LEVEL = DetailLevel.MIN;
    private static final int TIMEOUT_SECONDS = 60;
    private static final int NO_GROUP = -1;

    public static @Nullable CompletableFuture<long[][]> start(int blockX, int blockY, int blockZ, int face) {
        DimensionRuntime runtime = ClientSession.runtime();
        EminusInstance instance = ClientSession.instance();
        if (runtime == null || instance == null) {
            return null;
        }

        CellFrame frame = runtime.frame();
        long key = frame.keyAt(LEVEL, blockX, blockY, blockZ);
        int voxelX = CellFrame.voxelX(blockX, LEVEL);
        int voxelY = frame.voxelY(blockY, LEVEL);
        int voxelZ = CellFrame.voxelZ(blockZ, LEVEL);
        ClientBakery baking = ClientBakery.start(Minecraft.getInstance());
        return CellMeshing.mesh(runtime, instance, baking, new long[] {key}, TIMEOUT_SECONDS)
                .thenApply(meshes -> covering(CellMeshing.meshOf(meshes, key), frame, face, voxelX, voxelY, voxelZ))
                .whenComplete((rows, failure) -> baking.stop());
    }

    private static long[][] covering(CellMesh mesh, CellFrame frame, int face, int voxelX, int voxelY,
            int voxelZ) {
        Direction.Axis normal = Direction.from3DDataValue(face).getAxis();
        Direction.Axis width = PlaneAxes.width(normal);
        Direction.Axis height = PlaneAxes.height(normal);
        List<long[]> rows = new ArrayList<>();
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            if (Quad.face(quad) != face) {
                continue;
            }
            int x = Quad.x(quad);
            int y = Quad.y(quad);
            int z = Quad.z(quad);
            if (normal.choose(x, y, z) != normal.choose(voxelX, voxelY, voxelZ)
                    || !spans(width.choose(x, y, z), Quad.width(quad), width.choose(voxelX, voxelY, voxelZ))
                    || !spans(height.choose(x, y, z), Quad.height(quad), height.choose(voxelX, voxelY, voxelZ))) {
                continue;
            }
            long[] row = new long[FIELDS];
            row[BLOCK_X] = CellFrame.blockXOf(mesh.key(), x);
            row[BLOCK_Y] = frame.blockYOf(mesh.key(), y);
            row[BLOCK_Z] = CellFrame.blockZOf(mesh.key(), z);
            row[WIDTH] = Quad.width(quad);
            row[HEIGHT] = Quad.height(quad);
            row[WORD] = mesh.occlusion().length == 0 ? 0 : mesh.occlusion(index);
            row[LIGHT] = Quad.light(quad);
            row[MODEL_ID] = Quad.modelId(quad);
            row[GROUP] = groupOf(mesh, index);
            rows.add(row);
        }
        return rows.toArray(long[][]::new);
    }

    private static boolean spans(int start, int extent, int voxel) {
        return voxel >= start && voxel < start + extent;
    }

    private static int groupOf(CellMesh mesh, int index) {
        for (int group = 0; group < QuadGroups.COUNT; group++) {
            int start = mesh.groupStart(group);
            if (index >= start && index < start + mesh.groupCount(group)) {
                return group;
            }
        }
        return NO_GROUP;
    }

    private FaceWordReading() {
    }
}
