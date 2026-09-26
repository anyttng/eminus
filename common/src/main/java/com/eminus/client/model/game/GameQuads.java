package com.eminus.client.model.game;

import java.util.List;

import com.eminus.model.port.ModelQuad;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

import org.joml.Vector3fc;

final class GameQuads {
    private static final Direction[] FACES = Direction.values();

    static void gather(List<?> parts, List<ModelQuad> into) {
        for (Object part : parts) {
            BlockStateModelPart modelPart = (BlockStateModelPart) part;
            add(modelPart.getQuads(null), into);
            for (Direction face : FACES) {
                add(modelPart.getQuads(face), into);
            }
        }
    }

    static ModelQuad of(BakedQuad quad) {
        Vector3fc[] corners = new Vector3fc[ModelQuad.CORNERS];
        float[] uvs = new float[ModelQuad.CORNERS * 2];

        for (int corner = 0; corner < ModelQuad.CORNERS; corner++) {
            corners[corner] = quad.position(corner);
            long packed = quad.packedUV(corner);
            uvs[corner * 2] = UVPair.unpackU(packed);
            uvs[corner * 2 + 1] = UVPair.unpackV(packed);
        }

        BakedQuad.MaterialInfo material = quad.materialInfo();
        return new ModelQuad(corners, uvs, new GameSprite(material.sprite()), material.tintIndex(),
                material.layer().translucent(), material.lightEmission(), quad.direction());
    }

    private static void add(List<BakedQuad> quads, List<ModelQuad> into) {
        for (BakedQuad quad : quads) {
            into.add(of(quad));
        }
    }

    private GameQuads() {
    }
}
