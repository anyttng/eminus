package com.eminus.client.model;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.eminus.client.model.game.GameModels;
import com.eminus.model.BakedModel;
import com.eminus.model.ModelBakery;
import com.eminus.model.ShapeDivergence;
import com.eminus.model.port.BlockModels;
import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.Sprite;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class ShapeReading {
    public static final String FILE_NAME = "eminus-shapes.csv";
    public static final String CLASSES_FILE_NAME = "eminus-shape-classes.csv";

    public static final int STATES = 0;
    public static final int DRAWN = 1;
    public static final int EXACT = 2;
    public static final int FLATTENED = 3;
    public static final int BOUNDS_OFF = 4;
    public static final int LOST = 5;
    public static final int TILTED = 6;
    public static final int BLADED = 7;
    public static final int UNDRAWN = 8;
    public static final int FLUIDS = 9;
    public static final int FLUIDS_OFF = 10;
    public static final int CLASSES = 11;
    private static final int SUMMARY_WIDTH = 12;

    private static final String THREAD_NAME = "eminus-shape-reading";
    private static final float EPSILON = 1.0E-3F;
    private static final int BAKE_TIMEOUT_SECONDS = 60;
    private static final int ALL_MODELS = Integer.MAX_VALUE;
    private static final float NO_FLUID = -1.0F;
    private static final String SEPARATOR = ",";
    private static final String QUOTE = "\"";
    private static final String FACE_SEPARATOR = "|";
    private static final String ABSENT = "";
    private static final String NUMBER = "%.4f";
    private static final Direction[] FACES = Direction.values();

    private static final String STATE_HEADER = "block,state,class,model,positional,quads,planes,tilted,blades,"
            + "lost_faces,depth_down,depth_up,depth_north,depth_south,depth_west,depth_east,"
            + "bounds_x,bounds_y,bounds_z,bladed,blade_scale,fluid_height,fluid_top";
    private static final String CLASS_HEADER = "class,states,depth,depth_state,bounds_x,bounds_y,bounds_z,"
            + "planes,tilted_states,lost_states,bladed_states,blade_scale,undrawn_states,fluid_off";

    public static CompletableFuture<List<long[]>> start() {
        Minecraft client = Minecraft.getInstance();
        ClientBakery baking = ClientBakery.start(client);
        BlockModels models = GameModels.of(client, baking.cutoutLeaves()).blocks();
        Path directory = client.gameDirectory.toPath();

        CompletableFuture<List<long[]>> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                result.complete(run(baking.bakery(), models, directory));
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            } finally {
                baking.stop();
            }
        }, THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    private static List<long[]> run(ModelBakery bakery, BlockModels models, Path directory) {
        List<BlockState> states = ModelReading.everyState();
        ModelReading.bake(bakery, states, ALL_MODELS);

        long[] summary = new long[SUMMARY_WIDTH];
        Map<String, ClassRow> classes = new TreeMap<>();
        List<ModelQuad> quads = new ArrayList<>();

        try (BufferedWriter writer = Files.newBufferedWriter(directory.resolve(FILE_NAME))) {
            writer.write(STATE_HEADER);
            writer.newLine();

            for (BlockState state : states) {
                quads.clear();
                if (state.getRenderShape() != RenderShape.INVISIBLE) {
                    models.model(state).quads(RandomSource.create(state.getSeed(BlockPos.ZERO)), quads);
                }

                boolean positional = bakery.positional(state);
                int modelId = positional ? positionalModelId(bakery, state) : bakery.modelId(state);
                if (modelId < 0) {
                    throw new IllegalStateException("No model was baked for " + state + ".");
                }

                BakedModel baked = bakery.model(modelId);
                ShapeDivergence divergence = ShapeDivergence.measure(quads, baked, ShapeReading::spriteColumns);
                float fluidHeight = NO_FLUID;
                float fluidTop = NO_FLUID;
                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty()) {
                    fluidHeight = fluid.getOwnHeight();
                    int fluidId = bakery.fluidModelId(state);
                    BakedModel surface = fluidId >= 0 ? bakery.model(fluidId) : baked;
                    fluidTop = 1.0F - surface.insets()[Direction.UP.ordinal()];
                }

                writeState(writer, state, modelId, positional, divergence, fluidHeight, fluidTop);
                count(summary, state, divergence, fluidHeight, fluidTop);
                classes.computeIfAbsent(state.getBlock().getClass().getSimpleName(), name -> new ClassRow())
                        .add(state, divergence, fluidHeight, fluidTop);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }

        writeClasses(directory.resolve(CLASSES_FILE_NAME), classes);
        summary[CLASSES] = classes.size();
        return List.of(summary);
    }

    private static int positionalModelId(ModelBakery bakery, BlockState state) {
        CountDownLatch served = new CountDownLatch(1);
        int modelId = bakery.positionalModelId(state, 0, 0, 0, served::countDown);
        if (modelId != ModelBakery.MISSING) {
            return modelId;
        }

        try {
            if (!served.await(BAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The bake of " + state + " did not finish within "
                        + BAKE_TIMEOUT_SECONDS + " seconds.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while baking " + state + ".", interrupted);
        }

        return bakery.positionalModelId(state, 0, 0, 0, () -> { });
    }

    private static float spriteColumns(ModelQuad quad, float uSpan) {
        Sprite sprite = quad.sprite();
        float spriteSpan = sprite.u1() - sprite.u0();
        return spriteSpan <= 0.0F ? 0.0F : uSpan / spriteSpan * sprite.width();
    }

    private static void count(long[] summary, BlockState state, ShapeDivergence divergence, float fluidHeight,
            float fluidTop) {
        summary[STATES]++;
        boolean flattened = divergence.deepest() >= EPSILON;
        boolean boundsOff = boundsOff(divergence);
        boolean bladeOff = divergence.blades() > 0 && divergence.bladeDrift() >= EPSILON;

        if (divergence.quads() > 0) {
            summary[DRAWN]++;
            if (!flattened && !boundsOff && divergence.lostFaces() == 0 && divergence.tilted() == 0 && !bladeOff) {
                summary[EXACT]++;
            }
        } else if (undrawn(state, divergence)) {
            summary[UNDRAWN]++;
        }

        summary[FLATTENED] += flattened ? 1 : 0;
        summary[BOUNDS_OFF] += boundsOff ? 1 : 0;
        summary[LOST] += divergence.lostFaces() != 0 ? 1 : 0;
        summary[TILTED] += divergence.tilted() > 0 ? 1 : 0;
        summary[BLADED] += divergence.blades() > 0 ? 1 : 0;
        if (fluidHeight != NO_FLUID) {
            summary[FLUIDS]++;
            summary[FLUIDS_OFF] += Math.abs(fluidHeight - fluidTop) >= EPSILON ? 1 : 0;
        }
    }

    private static boolean undrawn(BlockState state, ShapeDivergence divergence) {
        return divergence.quads() == 0 && state.getRenderShape() == RenderShape.MODEL
                && state.getFluidState().isEmpty();
    }

    private static boolean boundsOff(ShapeDivergence divergence) {
        for (float axis : divergence.bounds()) {
            if (axis >= EPSILON) {
                return true;
            }
        }

        return false;
    }

    private static void writeState(BufferedWriter writer, BlockState state, int modelId, boolean positional,
            ShapeDivergence divergence, float fluidHeight, float fluidTop) throws IOException {
        StringJoiner row = new StringJoiner(SEPARATOR);
        row.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        row.add(QUOTE + state + QUOTE);
        row.add(state.getBlock().getClass().getSimpleName());
        row.add(Integer.toString(modelId));
        row.add(Boolean.toString(positional));
        row.add(Integer.toString(divergence.quads()));
        row.add(Integer.toString(divergence.planes()));
        row.add(Integer.toString(divergence.tilted()));
        row.add(Integer.toString(divergence.blades()));
        row.add(faces(divergence.lostFaces()));
        for (float depth : divergence.depth()) {
            row.add(number(depth));
        }

        for (float axis : divergence.bounds()) {
            row.add(number(axis));
        }

        row.add(Boolean.toString(divergence.bladed()));
        row.add(number(divergence.bladeScale()));
        row.add(fluidHeight == NO_FLUID ? ABSENT : number(fluidHeight));
        row.add(fluidTop == NO_FLUID ? ABSENT : number(fluidTop));
        writer.write(row.toString());
        writer.newLine();
    }

    private static void writeClasses(Path file, Map<String, ClassRow> classes) {
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write(CLASS_HEADER);
            writer.newLine();

            for (Map.Entry<String, ClassRow> entry : classes.entrySet()) {
                ClassRow row = entry.getValue();
                StringJoiner line = new StringJoiner(SEPARATOR);
                line.add(entry.getKey());
                line.add(Integer.toString(row.states));
                line.add(number(row.depth));
                line.add(QUOTE + row.depthState + QUOTE);
                for (float axis : row.bounds) {
                    line.add(number(axis));
                }

                line.add(Integer.toString(row.planes));
                line.add(Integer.toString(row.tilted));
                line.add(Integer.toString(row.lost));
                line.add(Integer.toString(row.bladed));
                line.add(number(row.bladeScale));
                line.add(Integer.toString(row.undrawn));
                line.add(number(row.fluidOff));
                writer.write(line.toString());
                writer.newLine();
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String faces(int mask) {
        StringJoiner names = new StringJoiner(FACE_SEPARATOR);
        for (Direction face : FACES) {
            if ((mask & 1 << face.ordinal()) != 0) {
                names.add(face.getSerializedName());
            }
        }

        return names.toString();
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, NUMBER, value);
    }

    private static final class ClassRow {
        private int states;
        private float depth;
        private String depthState = ABSENT;
        private final float[] bounds = new float[ShapeDivergence.AXES];
        private int planes;
        private int tilted;
        private int lost;
        private int bladed;
        private float bladeScale = ShapeDivergence.NO_BLADES;
        private int undrawn;
        private float fluidOff;

        private void add(BlockState state, ShapeDivergence divergence, float fluidHeight, float fluidTop) {
            states++;
            if (divergence.deepest() > depth) {
                depth = divergence.deepest();
                depthState = state.toString();
            }

            for (int axis = 0; axis < ShapeDivergence.AXES; axis++) {
                bounds[axis] = Math.max(bounds[axis], divergence.bounds()[axis]);
            }

            planes = Math.max(planes, divergence.planes());
            tilted += divergence.tilted() > 0 ? 1 : 0;
            lost += divergence.lostFaces() != 0 ? 1 : 0;
            bladed += divergence.blades() > 0 ? 1 : 0;
            if (divergence.bladeDrift() > Math.abs(bladeScale - ShapeDivergence.NO_BLADES)) {
                bladeScale = divergence.bladeScale();
            }

            undrawn += undrawn(state, divergence) ? 1 : 0;
            if (fluidHeight != NO_FLUID) {
                fluidOff = Math.max(fluidOff, Math.abs(fluidHeight - fluidTop));
            }
        }
    }

    private ShapeReading() {
    }
}
