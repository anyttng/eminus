package com.eminus.client.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.eminus.VanillaBootstrap;
import com.eminus.model.port.ModelQuad;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;

import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GameQuadsTest {
    private static final int TINT_LAYER = 2;
    private static final int EMISSION = 7;
    private static final float[] UVS = {0.125F, 0.25F, 0.375F, 0.5F, 0.625F, 0.75F, 0.875F, 1.0F};

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aQuadKeepsItsCornersUvsTintTranslucencyEmissionAndFace() {
        BakedQuad game = quad(Direction.EAST,
                new BakedQuad.MaterialInfo(null, ChunkSectionLayer.TRANSLUCENT, null, TINT_LAYER, true,
                        EMISSION));

        ModelQuad quad = GameQuads.of(game);

        for (int corner = 0; corner < ModelQuad.CORNERS; corner++) {
            assertEquals(game.position(corner), quad.corner(corner), "corner " + corner);
            assertEquals(UVS[corner * 2], quad.u(corner), "u " + corner);
            assertEquals(UVS[corner * 2 + 1], quad.v(corner), "v " + corner);
        }

        assertEquals(TINT_LAYER, quad.tintLayer());
        assertTrue(quad.tinted());
        assertTrue(quad.translucent());
        assertEquals(EMISSION, quad.emission());
        assertSame(Direction.EAST, quad.face());
        assertEquals(new GameSprite(null), quad.sprite());
    }

    @Test
    void anUntintedSolidQuadReadsAsNeither() {
        ModelQuad quad = GameQuads.of(quad(Direction.UP,
                new BakedQuad.MaterialInfo(null, ChunkSectionLayer.SOLID, null, ModelQuad.NO_TINT, true, 0)));

        assertEquals(ModelQuad.NO_TINT, quad.tintLayer());
        assertFalse(quad.tinted());
        assertFalse(quad.translucent());
    }

    @Test
    void aPartsUnculledQuadsComeBeforeItsFaceQuads() {
        BakedQuad.MaterialInfo material =
                new BakedQuad.MaterialInfo(null, ChunkSectionLayer.SOLID, null, ModelQuad.NO_TINT, true, 0);
        BakedQuad unculled = quad(Direction.UP, material);
        BakedQuad north = quad(Direction.NORTH, material);
        List<ModelQuad> into = new ArrayList<>();

        GameQuads.gather(List.of(new Part(north, unculled)), into);

        assertEquals(List.of(Direction.UP, Direction.NORTH), into.stream().map(ModelQuad::face).toList());
    }

    private static BakedQuad quad(Direction face, BakedQuad.MaterialInfo material) {
        return new BakedQuad(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                UVPair.pack(UVS[0], UVS[1]),
                UVPair.pack(UVS[2], UVS[3]),
                UVPair.pack(UVS[4], UVS[5]),
                UVPair.pack(UVS[6], UVS[7]),
                face,
                material);
    }

    private record Part(BakedQuad north, BakedQuad unculled) implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(Direction direction) {
            if (direction == null) {
                return List.of(unculled);
            }

            return direction == Direction.NORTH ? List.of(north) : List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public Material.Baked particleMaterial() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }
}
