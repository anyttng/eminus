package com.eminus.client.model.game;

import java.util.List;

import com.eminus.model.port.ModelQuad;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class GameQuads {
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST};
    private static final int VERTEX_INTS = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
    private static final int POSITION_INT = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.POSITION)
            / Integer.BYTES;
    private static final int UV_INT = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.UV0) / Integer.BYTES;
    private static final int NO_EMISSION = 0;

    @FunctionalInterface
    public interface SideQuads {
        List<BakedQuad> quads(@Nullable Direction side, RandomSource random);
    }

    public static void gather(ReplayRandom random, boolean translucent, SideQuads sides,
            List<? super LayeredQuad> into) {
        for (Direction side : SIDES) {
            random.restart();
            for (BakedQuad quad : sides.quads(side, random)) {
                into.add(new LayeredQuad(quad, translucent));
            }
        }
    }

    public static void gatherByBlockLayer(BlockState state, BakedModel model, RandomSource random,
            List<? super LayeredQuad> into) {
        boolean translucent = ItemBlockRenderTypes.getChunkRenderType(state) == RenderType.translucent();
        gather(new ReplayRandom(random), translucent, (side, replay) -> model.getQuads(state, side, replay), into);
    }

    static void convert(List<?> quads, List<ModelQuad> into) {
        for (Object quad : quads) {
            into.add(of((LayeredQuad) quad));
        }
    }

    static ModelQuad of(LayeredQuad layered) {
        BakedQuad quad = layered.quad();
        int[] vertices = quad.getVertices();
        Vector3fc[] corners = new Vector3fc[ModelQuad.CORNERS];
        float[] uvs = new float[ModelQuad.CORNERS * 2];

        for (int corner = 0; corner < ModelQuad.CORNERS; corner++) {
            int position = corner * VERTEX_INTS + POSITION_INT;
            corners[corner] = new Vector3f(Float.intBitsToFloat(vertices[position]),
                    Float.intBitsToFloat(vertices[position + 1]), Float.intBitsToFloat(vertices[position + 2]));
            int uv = corner * VERTEX_INTS + UV_INT;
            uvs[corner * 2] = Float.intBitsToFloat(vertices[uv]);
            uvs[corner * 2 + 1] = Float.intBitsToFloat(vertices[uv + 1]);
        }

        return new ModelQuad(corners, uvs, new GameSprite(quad.getSprite()), quad.getTintIndex(),
                layered.translucent(), NO_EMISSION, quad.getDirection());
    }

    private GameQuads() {
    }
}
