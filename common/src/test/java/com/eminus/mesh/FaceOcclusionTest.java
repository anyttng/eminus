package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FaceOcclusionTest {
    private static final int TRIALS = 500;
    private static final long SEED = 248L;
    private static final int REACH = CellVoxels.APRON;
    private static final int INSIDE = 8;
    private static final int LAST = DetailLevel.VOXELS_PER_SIDE - 1;
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int BIOME = 0;
    private static final int STONE = 1;
    private static final int SLAB = 5;
    private static final float FULL = 1.0F;
    private static final float HALF = 0.5F;
    private static final float NO_INSET = 0.0F;
    private static final double ONE_STEP = 1.5 / 255.0;
    private static final double LIT = 1.0;
    private static final double DARK = 0.2;
    private static final int BLOCKS_AROUND_VERTEX = 4;
    private static final int VERTICES = 4;
    private static final int VERTEX_INTS = 8;
    private static final int SHAPE_FLAGS = 3;
    private static final float EVEN = 1.0F;
    private static final boolean SHADED = true;
    private static final String AMBIENT_FACE = ModelBlockRenderer.class.getName() + "$AmbientOcclusionFace";
    private static final String CALCULATE_SHAPE = "calculateShape";
    private static final String CALCULATE = "calculate";
    private static final String BRIGHTNESS = "brightness";
    private static final LevelLightEngine NO_LIGHT = new LevelLightEngine(new LightChunkGetter() {
        @Override
        public @Nullable LightChunk getChunkForLighting(int chunkX, int chunkZ) {
            return null;
        }

        @Override
        public BlockGetter getLevel() {
            return EmptyBlockGetter.INSTANCE;
        }
    }, false, false);

    private static List<BlockState> palette;
    private static ModelBlockRenderer renderer;
    private static Method calculateShape;
    private static Constructor<?> ambientFace;
    private static Method calculate;
    private static Field brightness;

    private final FakeModels models = new FakeModels();

    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        VanillaBootstrap.ensure();
        palette = List.of(Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                Blocks.MUD.defaultBlockState(),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                Blocks.ICE.defaultBlockState());

        renderer = new ModelBlockRenderer(BlockColors.createDefault());
        calculateShape = ModelBlockRenderer.class.getDeclaredMethod(CALCULATE_SHAPE, BlockAndTintGetter.class,
                BlockState.class, BlockPos.class, int[].class, Direction.class, float[].class, BitSet.class);
        calculateShape.setAccessible(true);
        Class<?> face = Class.forName(AMBIENT_FACE);
        ambientFace = face.getDeclaredConstructor();
        ambientFace.setAccessible(true);
        calculate = face.getDeclaredMethod(CALCULATE, BlockAndTintGetter.class, BlockState.class, BlockPos.class,
                Direction.class, float[].class, BitSet.class, boolean.class);
        calculate.setAccessible(true);
        brightness = face.getDeclaredField(BRIGHTNESS);
        brightness.setAccessible(true);
    }

    @Test
    void aFullFaceDarkensItsCornersAsTheGameDoes() throws ReflectiveOperationException {
        Random random = new Random(SEED);
        for (int trial = 0; trial < TRIALS; trial++) {
            BlockPos centre = new BlockPos(INSIDE, INSIDE, INSIDE);
            Map<BlockPos, Integer> world = around(random, centre, STONE);
            for (Direction face : Direction.values()) {
                assertMatches(world, centre, face, FULL, NO_INSET, trial);
            }
        }
    }

    @Test
    void aFullFaceAtTheCellCornerReadsTheNeighbourCells() throws ReflectiveOperationException {
        Random random = new Random(SEED + 1);
        for (int trial = 0; trial < TRIALS; trial++) {
            BlockPos centre = new BlockPos(LAST, LAST, LAST);
            Map<BlockPos, Integer> world = around(random, centre, STONE);
            for (Direction face : Direction.values()) {
                assertMatches(world, centre, face, FULL, NO_INSET, trial);
            }
        }
    }

    @Test
    void aSlabTopReadsItsOwnLayer() throws ReflectiveOperationException {
        Random random = new Random(SEED + 2);
        for (int trial = 0; trial < TRIALS; trial++) {
            BlockPos centre = new BlockPos(INSIDE, INSIDE, INSIDE);
            Map<BlockPos, Integer> world = around(random, centre, SLAB);
            assertMatches(world, centre, Direction.UP, HALF, HALF, trial);
        }
    }

    private Map<BlockPos, Integer> around(Random random, BlockPos centre, int stateId) {
        Map<BlockPos, Integer> world = new HashMap<>();
        for (int dy = -REACH; dy <= REACH; dy++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                for (int dx = -REACH; dx <= REACH; dx++) {
                    world.put(centre.offset(dx, dy, dz), random.nextInt(palette.size()));
                }
            }
        }

        world.put(centre, stateId);
        return world;
    }

    private void assertMatches(Map<BlockPos, Integer> world, BlockPos centre, Direction face, float top, float inset,
            int trial) throws ReflectiveOperationException {
        for (int id = 1; id < palette.size(); id++) {
            models.shadeAs(id, StateShade.of(palette.get(id)));
        }

        int stateId = world.get(centre);
        int word = new FaceOcclusion(voxels(world), models, DetailLevel.MIN)
                .face(face, centre.getX(), centre.getY(), centre.getZ(), stateId, ModelMetadata.SMOOTH,
                        face == Direction.UP ? inset : NO_INSET);

        Vector3fc[] corners = corners(face, top);
        float[] game = gameBrightness(level(world), palette.get(stateId), centre, face, corners);

        Direction.Axis width = PlaneAxes.width(face.getAxis());
        Direction.Axis height = PlaneAxes.height(face.getAxis());
        for (int vertex = 0; vertex < VERTICES; vertex++) {
            boolean highU = along(corners[vertex], width) > HALF;
            boolean highV = along(corners[vertex], height) > HALF;
            assertEquals(game[vertex], value(word, highU, highV), ONE_STEP,
                    "trial " + trial + ", " + face + " face, vertex " + vertex + ", word " + word);
        }
    }

    private static float[] gameBrightness(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction face,
            Vector3fc[] corners) throws ReflectiveOperationException {
        int[] vertices = new int[VERTICES * VERTEX_INTS];
        for (int vertex = 0; vertex < VERTICES; vertex++) {
            int base = vertex * VERTEX_INTS;
            vertices[base] = Float.floatToRawIntBits(corners[vertex].x());
            vertices[base + 1] = Float.floatToRawIntBits(corners[vertex].y());
            vertices[base + 2] = Float.floatToRawIntBits(corners[vertex].z());
        }

        float[] shape = new float[Direction.values().length * 2];
        BitSet flags = new BitSet(SHAPE_FLAGS);
        calculateShape.invoke(renderer, level, state, pos, vertices, face, shape, flags);
        Object ambient = ambientFace.newInstance();
        calculate.invoke(ambient, level, state, pos, face, shape, flags, SHADED);
        return (float[]) brightness.get(ambient);
    }

    private CellVoxels voxels(Map<BlockPos, Integer> world) {
        CellVoxels voxels = new CellVoxels();
        voxels.load(cell(world, 0, 0, 0));
        for (int stepY = -1; stepY <= 1; stepY++) {
            for (int stepZ = -1; stepZ <= 1; stepZ++) {
                for (int stepX = -1; stepX <= 1; stepX++) {
                    if (stepX != 0 || stepY != 0 || stepZ != 0) {
                        voxels.loadRegion(stepX, stepY, stepZ, cell(world, stepX, stepY, stepZ));
                    }
                }
            }
        }

        return voxels;
    }

    private static Cell cell(Map<BlockPos, Integer> world, int cellX, int cellY, int cellZ) {
        Cell cell = Cell.blank(CellKey.pack(DetailLevel.MIN, cellX, cellY, cellZ));
        world.forEach((pos, id) -> {
            int x = pos.getX() - cellX * SIDE;
            int y = pos.getY() - cellY * SIDE;
            int z = pos.getZ() - cellZ * SIDE;
            if (id != VoxelEntry.AIR_STATE_ID && inside(x) && inside(y) && inside(z)) {
                cell.set(x, y, z, VoxelEntry.pack(id, BIOME, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0)));
            }
        });
        return cell;
    }

    private static boolean inside(int coordinate) {
        return coordinate >= 0 && coordinate < SIDE;
    }

    private static double value(int word, boolean highU, boolean highV) {
        int side = highU ? OcclusionWord.HIGH_U : OcclusionWord.LOW_U;
        int up = highV ? OcclusionWord.HIGH_V : OcclusionWord.LOW_V;
        int corner = highU
                ? highV ? OcclusionWord.HIGH_U_HIGH_V : OcclusionWord.HIGH_U_LOW_V
                : highV ? OcclusionWord.LOW_U_HIGH_V : OcclusionWord.LOW_U_LOW_V;
        return (shade(word, side) + shade(word, up) + shade(word, corner) + shade(word, OcclusionWord.FRONT))
                / BLOCKS_AROUND_VERTEX;
    }

    private static double shade(int word, int bit) {
        return (word & bit) != 0 ? DARK : LIT;
    }

    private static float along(Vector3fc point, Direction.Axis axis) {
        return switch (axis) {
            case X -> point.x();
            case Y -> point.y();
            case Z -> point.z();
        };
    }

    private static Vector3fc[] corners(Direction face, float top) {
        float[] bounds = new float[Direction.values().length];
        bounds[Direction.UP.get3DDataValue()] = top;
        bounds[Direction.SOUTH.get3DDataValue()] = FULL;
        bounds[Direction.EAST.get3DDataValue()] = FULL;

        FaceInfo info = FaceInfo.fromFacing(face);
        Vector3fc[] corners = new Vector3fc[VERTICES];
        for (int vertex = 0; vertex < VERTICES; vertex++) {
            FaceInfo.VertexInfo corner = info.getVertexInfo(vertex);
            corners[vertex] = new Vector3f(bounds[corner.xFace], bounds[corner.yFace], bounds[corner.zFace]);
        }

        return corners;
    }

    private static BlockAndTintGetter level(Map<BlockPos, Integer> world) {
        return new BlockAndTintGetter() {
            @Override
            public float getShade(Direction direction, boolean shade) {
                return EVEN;
            }

            @Override
            public LevelLightEngine getLightEngine() {
                return NO_LIGHT;
            }

            @Override
            public int getBlockTint(BlockPos pos, ColorResolver color) {
                return -1;
            }

            @Override
            public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return palette.get(world.getOrDefault(pos, VoxelEntry.AIR_STATE_ID));
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return getBlockState(pos).getFluidState();
            }

            @Override
            public int getHeight() {
                return 0;
            }

            @Override
            public int getMinBuildHeight() {
                return 0;
            }
        };
    }
}
