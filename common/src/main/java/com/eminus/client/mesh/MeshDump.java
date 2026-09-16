package com.eminus.client.mesh;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.ObjWriter;
import com.eminus.mesh.QuadGroups;
import com.eminus.client.model.ClientBakery;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;
import com.eminus.client.session.ClientSession;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

public final class MeshDump {
    public static final String FILE_NAME = "eminus-cell.obj";

    private static final String PROBE = "[eminus-mesh]";
    private static final int TIMEOUT_SECONDS = 60;
    private static final String NO_SESSION = "No dimension runtime is open.";
    private static final String NO_MESH = "The cell did not mesh within " + TIMEOUT_SECONDS + " seconds.";
    private static final String SEPARATOR = "/";

    public static Component at(int level) {
        Minecraft client = Minecraft.getInstance();
        DimensionRuntime runtime = ClientSession.runtime();
        EminusInstance instance = ClientSession.instance();

        if (runtime == null || instance == null || client.player == null) {
            Eminus.LOGGER.info("{} dump level={} skipped=no-session", PROBE, level);
            return Component.literal(NO_SESSION);
        }

        BlockPos pos = client.player.blockPosition();
        long key = runtime.frame().keyAt(level, pos.getX(), pos.getY(), pos.getZ());
        Path file = client.gameDirectory.toPath().resolve(FILE_NAME);
        Eminus.LOGGER.info("{} dump level={} cell=({}, {}, {}) file={}",
                PROBE, level, CellKey.x(key), CellKey.y(key), CellKey.z(key), file);

        ClientBakery baking = ClientBakery.start(client);

        try {
            CellMesh mesh = build(runtime, instance, baking, key);
            return mesh == null ? Component.literal(NO_MESH) : write(mesh, file);
        } finally {
            baking.stop();
        }
    }

    private static @Nullable CellMesh build(DimensionRuntime runtime, EminusInstance instance,
            ClientBakery baking, long key) {
        try {
            return CellMeshing.mesh(runtime, instance, baking, new long[] {key}, TIMEOUT_SECONDS).get().get(key);
        } catch (ExecutionException stalled) {
            Eminus.LOGGER.error("{} stalled cell=({}, {}, {})", PROBE, CellKey.x(key), CellKey.y(key), CellKey.z(key));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        return null;
    }

    private static Component write(CellMesh mesh, Path file) {
        try {
            ObjWriter.write(mesh, file);
        } catch (IOException failure) {
            Eminus.LOGGER.error("{} failed file={}", PROBE, file, failure);
            return Component.literal(failure.toString());
        }

        Eminus.LOGGER.info("{} done quads={} groups={}", PROBE, mesh.quadCount(), groups(mesh));
        return Component.literal(file.toString());
    }

    private static String groups(CellMesh mesh) {
        StringBuilder counts = new StringBuilder();

        for (int group = 0; group < QuadGroups.COUNT; group++) {
            counts.append(group == 0 ? "" : SEPARATOR).append(mesh.groupCount(group));
        }

        return counts.toString();
    }

    private MeshDump() {
    }
}
