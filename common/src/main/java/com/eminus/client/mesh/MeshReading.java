package com.eminus.client.mesh;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import com.eminus.cell.CellKey;
import com.eminus.client.model.ClientBakery;
import com.eminus.client.session.ClientSession;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.ObjWriter;
import com.eminus.mesh.QuadGroups;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;

public final class MeshReading {
    public static final String FILE_NAME = "eminus-cell.obj";

    public static final int LEVEL = 0;
    public static final int CELL_X = 1;
    public static final int CELL_Y = 2;
    public static final int CELL_Z = 3;
    public static final int QUADS = 4;
    public static final int FIRST_GROUP = 5;

    private static final int TIMEOUT_SECONDS = 60;

    public static @Nullable CompletableFuture<long[]> start(int level, int blockX, int blockY, int blockZ) {
        Minecraft client = Minecraft.getInstance();
        DimensionRuntime runtime = ClientSession.runtime();
        EminusInstance instance = ClientSession.instance();
        if (runtime == null || instance == null) {
            return null;
        }

        long key = runtime.frame().keyAt(level, blockX, blockY, blockZ);
        Path file = client.gameDirectory.toPath().resolve(FILE_NAME);
        ClientBakery baking = ClientBakery.start(client);

        return CellMeshing.mesh(runtime, instance, baking, new long[] {key}, TIMEOUT_SECONDS)
                .thenApply(meshes -> write(meshes.get(key), file))
                .whenComplete((row, failure) -> baking.stop());
    }

    private static long[] write(CellMesh mesh, Path file) {
        try {
            ObjWriter.write(mesh, file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }

        long key = mesh.key();
        long[] row = new long[FIRST_GROUP + QuadGroups.COUNT];
        row[LEVEL] = CellKey.level(key);
        row[CELL_X] = CellKey.x(key);
        row[CELL_Y] = CellKey.y(key);
        row[CELL_Z] = CellKey.z(key);
        row[QUADS] = mesh.quadCount();
        for (int group = 0; group < QuadGroups.COUNT; group++) {
            row[FIRST_GROUP + group] = mesh.groupCount(group);
        }

        return row;
    }

    private MeshReading() {
    }
}
