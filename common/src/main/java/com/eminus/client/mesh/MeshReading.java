package com.eminus.client.mesh;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

import com.eminus.cell.CellKey;
import com.eminus.cell.StateLayers;
import com.eminus.cell.StateTable;
import com.eminus.client.model.ClientBakery;
import com.eminus.client.session.ClientSession;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshModels;
import com.eminus.mesh.ObjWriter;
import com.eminus.mesh.Quad;
import com.eminus.mesh.QuadGroups;
import com.eminus.model.BakedModel;
import com.eminus.model.ModelSource;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;

import org.jspecify.annotations.Nullable;

public final class MeshReading {
    public static final String FILE_NAME = "eminus-cell.obj";

    public static final int LEVEL = 0;
    public static final int CELL_X = 1;
    public static final int CELL_Y = 2;
    public static final int CELL_Z = 3;
    public static final int QUADS = 4;
    public static final int BLADE_QUADS = 5;
    public static final int FIRST_GROUP = 6;

    private static final int TIMEOUT_SECONDS = 60;
    private static final String LEGEND_PREFIX = "# m";
    private static final String QUAD_PREFIX = "# q ";
    private static final String RECORD_PREFIX = "# r ";
    private static final String FLOAT_SEPARATOR = ",";

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
                .thenApply(meshes -> write(CellMeshing.meshOf(meshes, key), MeshWiring.models(runtime, baking),
                        baking.bakery(), runtime.states(), file))
                .whenComplete((row, failure) -> baking.stop());
    }

    private static long[] write(CellMesh mesh, MeshModels models, ModelSource source, StateTable states,
            Path file) {
        try {
            ObjWriter.write(mesh, models, file);
            legend(models, states, file);
            raw(mesh, source, file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }

        return row(mesh);
    }

    private static void raw(CellMesh mesh, ModelSource source, Path file) throws IOException {
        IntSortedSet modelIds = new IntRBTreeSet();
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardOpenOption.APPEND)) {
            for (int group = 0; group < QuadGroups.COUNT; group++) {
                int start = mesh.groupStart(group);
                for (int index = start; index < start + mesh.groupCount(group); index++) {
                    long quad = mesh.quad(index);
                    out.write(QUAD_PREFIX + group + " " + quad + " " + mesh.placement(quad));
                    out.newLine();
                    modelIds.add(Quad.modelId(quad));
                }
            }

            for (int modelId : modelIds) {
                BakedModel model = source.model(modelId);
                if (model == null) {
                    continue;
                }
                out.write(RECORD_PREFIX + modelId + " " + model.metadata() + " " + floats(model.insets()) + " "
                        + floats(model.bounds()) + " " + floats(model.slopes()));
                out.newLine();
            }
        }
    }

    private static String floats(float[] values) {
        StringJoiner joined = new StringJoiner(FLOAT_SEPARATOR);
        for (float value : values) {
            joined.add(Float.toString(value));
        }
        return joined.toString();
    }

    private static void legend(MeshModels models, StateTable states, Path file) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardOpenOption.APPEND)) {
            for (int stateId = 0; stateId < states.size(); stateId++) {
                int block = models.modelId(stateId, MeshReading::notWaited);
                if (block == MeshModels.MISSING || block == MeshModels.POSITIONAL) {
                    continue;
                }

                String name = name(states, stateId);
                entry(out, block, "block", name);
                entry(out, models.opaqueModelId(block), "opaque", name);
                entry(out, models.submergedModelId(block), "submerged", name);
                int fluid = models.fluidModelId(stateId, MeshReading::notWaited);
                if (fluid != MeshModels.MISSING && fluid != MeshModels.NO_FLUID) {
                    entry(out, fluid, "fluid", name);
                    entry(out, models.submergedModelId(fluid), "fluid-submerged", name);
                }
            }
        }
    }

    private static String name(StateTable states, int stateId) {
        String block = BlockStateParser.serialize(states.state(stateId));
        int layer = states.layerBlocks(stateId);
        return layer == StateLayers.NOT_LAYERED ? block
                : block + " over " + BlockStateParser.serialize(states.state(states.layerFluid(stateId)))
                        + " blocks=" + layer;
    }

    private static void notWaited() {
    }

    private static void entry(BufferedWriter out, int modelId, String role, String name) throws IOException {
        out.write(LEGEND_PREFIX + modelId + " " + role + " " + name);
        out.newLine();
    }

    private static long[] row(CellMesh mesh) {
        long key = mesh.key();
        long[] row = new long[FIRST_GROUP + QuadGroups.COUNT];
        row[LEVEL] = CellKey.level(key);
        row[CELL_X] = CellKey.x(key);
        row[CELL_Y] = CellKey.y(key);
        row[CELL_Z] = CellKey.z(key);
        row[QUADS] = mesh.quadCount();
        for (int index = 0; index < mesh.quadCount(); index++) {
            row[BLADE_QUADS] += Quad.isBlade(mesh.quad(index)) ? 1 : 0;
        }

        for (int group = 0; group < QuadGroups.COUNT; group++) {
            row[FIRST_GROUP + group] = mesh.groupCount(group);
        }

        return row;
    }

    private MeshReading() {
    }
}
