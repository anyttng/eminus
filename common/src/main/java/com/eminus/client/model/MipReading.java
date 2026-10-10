package com.eminus.client.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.eminus.client.model.game.GameModels;
import com.eminus.client.model.game.SpriteMips;
import com.eminus.model.BakedModel;
import com.eminus.model.FaceMips;
import com.eminus.model.Mips;
import com.eminus.model.ModelBakery;
import com.eminus.model.ModelMetadata;
import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.Sprite;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

public final class MipReading {
    public static final int STATE_ID = 0;
    public static final int CUTOUT_LEAVES = 1;
    public static final int FACE = 2;
    public static final int STRATEGY = 3;
    public static final int OPAQUE = 4;
    public static final int WHOLE_SPRITE = 5;
    public static final int SYMMETRY = 6;
    public static final int LEVELS = 7;
    public static final int FIRST_OFF = 8;
    public static final int OFF_LEVEL = FIRST_OFF + Mips.levelCount(BakedModel.FACE_SIDE);
    public static final int OFF_TEXEL = OFF_LEVEL + 1;
    public static final int OFF_OURS = OFF_TEXEL + 1;
    public static final int OFF_GAME = OFF_OURS + 1;
    public static final int NOT_COMPARED = -1;
    private static final int ROW_WIDTH = OFF_GAME + 1;

    private static final String THREAD_NAME = "eminus-mip-reading";
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int SYMMETRIES = 8;
    private static final int FLIP_X = 1;
    private static final int FLIP_Y = 2;
    private static final int TRANSPOSE = 4;
    private static final float UV_EPSILON = 1.0E-5F;
    private static final boolean[] LEAF_MODES = {true, false};
    private static final Direction[] FACES = Direction.values();

    public static CompletableFuture<List<long[]>> start() {
        Minecraft client = Minecraft.getInstance();
        CompletableFuture<List<long[]>> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                List<long[]> rows = new ArrayList<>();
                for (boolean cutoutLeaves : LEAF_MODES) {
                    rows.addAll(run(client, cutoutLeaves));
                }

                result.complete(rows);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        }, THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    private static List<long[]> run(Minecraft client, boolean cutoutLeaves) {
        ClientBakery baking = ClientBakery.start(client, cutoutLeaves);
        GameModels game = GameModels.of(client, cutoutLeaves);
        List<long[]> rows = new ArrayList<>();

        try {
            List<BlockState> states = sample();
            ModelBakery bakery = baking.bakery();
            ModelReading.bake(bakery, states, Integer.MAX_VALUE);

            for (BlockState state : states) {
                BakedModel model = bakery.model(bakery.modelId(state));
                List<ModelQuad> quads = new ArrayList<>();
                game.blocks().model(state).quads(RandomSource.create(state.getSeed(BlockPos.ZERO)), quads);

                for (Direction face : FACES) {
                    rows.add(row(state, face, model, quads, cutoutLeaves));
                }
            }
        } finally {
            baking.stop();
        }

        return rows;
    }

    private static List<BlockState> sample() {
        List<BlockState> states = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof LeavesBlock) {
                states.add(block.defaultBlockState());
            }
        }

        states.add(Blocks.STONE.defaultBlockState());
        return states;
    }

    @SuppressWarnings("EnumOrdinal")
    private static long[] row(BlockState state, Direction face, BakedModel model, List<ModelQuad> quads,
            boolean cutoutLeaves) {
        int index = face.get3DDataValue();
        boolean opaque = ModelMetadata.has(model.metadata(), ModelMetadata.OPAQUE);
        long[] row = new long[ROW_WIDTH];
        Arrays.fill(row, FIRST_OFF, OFF_LEVEL, NOT_COMPARED);
        row[STATE_ID] = Block.getId(state);
        row[CUTOUT_LEAVES] = cutoutLeaves ? 1 : 0;
        row[FACE] = index;
        row[STRATEGY] = model.mips()[index].strategy().ordinal();
        row[OPAQUE] = opaque ? 1 : 0;
        row[SYMMETRY] = NOT_COMPARED;
        row[OFF_LEVEL] = NOT_COMPARED;

        ModelQuad quad = wholeSpriteQuad(quads, face);
        if (quad == null) {
            return row;
        }

        int[][] gameLevels = SpriteMips.levels(quad.sprite());
        if (gameLevels[0].length != BakedModel.FACE_TEXELS) {
            return row;
        }

        row[WHOLE_SPRITE] = 1;
        int[] face0 = new int[BakedModel.FACE_TEXELS];
        System.arraycopy(model.faces(), index * BakedModel.FACE_TEXELS, face0, 0, BakedModel.FACE_TEXELS);
        int[][] ours = FaceMips.colourLevels(face0, BakedModel.FACE_SIDE, model.mips()[index], opaque);
        int levels = Math.min(ours.length, gameLevels.length);
        row[LEVELS] = levels;

        int symmetry = 0;
        int fewest = Integer.MAX_VALUE;
        for (int candidate = 0; candidate < SYMMETRIES; candidate++) {
            int off = mismatches(ours[0], gameLevels[0], BakedModel.FACE_SIDE, candidate, opaque, null, 0);
            if (off < fewest) {
                fewest = off;
                symmetry = candidate;
            }
        }

        row[SYMMETRY] = symmetry;
        for (int level = 0; level < levels; level++) {
            row[FIRST_OFF + level] = mismatches(ours[level], gameLevels[level], BakedModel.FACE_SIDE >> level,
                    symmetry, opaque, row[OFF_LEVEL] == NOT_COMPARED ? row : null, level);
        }

        return row;
    }

    private static @Nullable ModelQuad wholeSpriteQuad(List<ModelQuad> quads, Direction face) {
        ModelQuad found = null;
        for (ModelQuad quad : quads) {
            if (quad.face() != face) {
                continue;
            }

            if (found != null) {
                return null;
            }

            found = quad;
        }

        return found != null && coversSprite(found) ? found : null;
    }

    private static boolean coversSprite(ModelQuad quad) {
        Sprite sprite = quad.sprite();
        float minU = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        for (int corner = 0; corner < ModelQuad.CORNERS; corner++) {
            minU = Math.min(minU, quad.u(corner));
            maxU = Math.max(maxU, quad.u(corner));
            minV = Math.min(minV, quad.v(corner));
            maxV = Math.max(maxV, quad.v(corner));
        }

        return Math.abs(minU - sprite.u0()) <= UV_EPSILON && Math.abs(maxU - sprite.u1()) <= UV_EPSILON
                && Math.abs(minV - sprite.v0()) <= UV_EPSILON && Math.abs(maxV - sprite.v1()) <= UV_EPSILON;
    }

    private static int mismatches(int[] ours, int[] game, int side, int symmetry, boolean opaque,
            long @Nullable [] firstOff, int level) {
        int off = 0;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int texel = y * side + x;
                int mine = ours[mapped(x, y, side, symmetry)];
                int theirs = opaque ? game[texel] | ALPHA_MASK : game[texel];
                if (mine == theirs) {
                    continue;
                }

                if (off == 0 && firstOff != null) {
                    firstOff[OFF_LEVEL] = level;
                    firstOff[OFF_TEXEL] = texel;
                    firstOff[OFF_OURS] = Integer.toUnsignedLong(mine);
                    firstOff[OFF_GAME] = Integer.toUnsignedLong(game[texel]);
                }

                off++;
            }
        }

        return off;
    }

    private static int mapped(int x, int y, int side, int symmetry) {
        int u = (symmetry & TRANSPOSE) != 0 ? y : x;
        int v = (symmetry & TRANSPOSE) != 0 ? x : y;
        u = (symmetry & FLIP_X) != 0 ? side - 1 - u : u;
        v = (symmetry & FLIP_Y) != 0 ? side - 1 - v : v;
        return v * side + u;
    }

    private MipReading() {
    }
}
