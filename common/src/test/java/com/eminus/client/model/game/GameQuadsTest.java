package com.eminus.client.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.eminus.VanillaBootstrap;
import com.eminus.model.port.ModelQuad;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GameQuadsTest {
    private static final int VERTEX_INTS = 8;
    private static final int UV_INT = 4;
    private static final int TINT_LAYER = 2;
    private static final int SIDES = 7;
    private static final long SEED = 42L;
    private static final float[][] CORNERS = {{0.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F}, {1.0F, 1.0F, 0.0F},
            {0.0F, 1.0F, 0.0F}};
    private static final float[] UVS = {0.125F, 0.25F, 0.375F, 0.5F, 0.625F, 0.75F, 0.875F, 1.0F};

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aQuadKeepsItsCornersUvsTintTranslucencyAndFace() {
        BakedQuad game = quad(Direction.EAST, TINT_LAYER);

        ModelQuad quad = GameQuads.of(new LayeredQuad(game, true));

        for (int corner = 0; corner < ModelQuad.CORNERS; corner++) {
            float[] position = CORNERS[corner];
            assertEquals(new Vector3f(position[0], position[1], position[2]), quad.corner(corner), "corner " + corner);
            assertEquals(UVS[corner * 2], quad.u(corner), "u " + corner);
            assertEquals(UVS[corner * 2 + 1], quad.v(corner), "v " + corner);
        }

        assertEquals(TINT_LAYER, quad.tintLayer());
        assertTrue(quad.tinted());
        assertTrue(quad.translucent());
        assertEquals(0, quad.emission());
        assertSame(Direction.EAST, quad.face());
        assertEquals(new GameSprite(null), quad.sprite());
    }

    @Test
    void anUntintedQuadOutsideTheTranslucentLayerReadsAsNeither() {
        ModelQuad quad = GameQuads.of(new LayeredQuad(quad(Direction.UP, ModelQuad.NO_TINT), false));

        assertEquals(ModelQuad.NO_TINT, quad.tintLayer());
        assertFalse(quad.tinted());
        assertFalse(quad.translucent());
    }

    @Test
    void theUnculledQuadsComeBeforeTheFaceQuads() {
        BakedQuad unculled = quad(Direction.UP, ModelQuad.NO_TINT);
        BakedQuad north = quad(Direction.NORTH, ModelQuad.NO_TINT);
        List<LayeredQuad> into = new ArrayList<>();

        GameQuads.gather(new ReplayRandom(RandomSource.create(SEED)), false, (side, random) -> {
            if (side == null) {
                return List.of(unculled);
            }

            return side == Direction.NORTH ? List.of(north) : List.of();
        }, into);

        assertEquals(List.of(new LayeredQuad(unculled, false), new LayeredQuad(north, false)), into);
    }

    @Test
    void everySideDrawsFromTheSeedTheCallerSet() {
        List<Long> draws = new ArrayList<>();

        GameQuads.gather(new ReplayRandom(RandomSource.create(SEED)), false, (side, random) -> {
            draws.add(random.nextLong());
            return List.of();
        }, new ArrayList<>());

        long expected = RandomSource.create(SEED).nextLong();
        assertEquals(SIDES, draws.size());
        for (long draw : draws) {
            assertEquals(expected, draw);
        }
    }

    private static BakedQuad quad(Direction face, int tintLayer) {
        int[] vertices = new int[ModelQuad.CORNERS * VERTEX_INTS];
        for (int corner = 0; corner < ModelQuad.CORNERS; corner++) {
            int base = corner * VERTEX_INTS;
            for (int axis = 0; axis < CORNERS[corner].length; axis++) {
                vertices[base + axis] = Float.floatToRawIntBits(CORNERS[corner][axis]);
            }

            vertices[base + UV_INT] = Float.floatToRawIntBits(UVS[corner * 2]);
            vertices[base + UV_INT + 1] = Float.floatToRawIntBits(UVS[corner * 2 + 1]);
        }

        return new BakedQuad(vertices, tintLayer, face, null, true);
    }
}
