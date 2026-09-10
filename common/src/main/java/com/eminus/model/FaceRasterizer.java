package com.eminus.model;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import com.eminus.cell.FaceMask;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

import org.joml.Vector3fc;

public final class FaceRasterizer {
    private static final float CENTRE = 0.5F;
    private static final float FLUSH = 1.0F / 256.0F;
    private static final float MIN_AREA = 1.0E-6F;
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int NO_TINT_LAYER = -1;

    private static final Direction[] FACES = Direction.values();
    private static final Direction[] U_AXES = {
        Direction.EAST, Direction.EAST, Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH};
    private static final Direction[] V_AXES = {
        Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.UP, Direction.UP, Direction.UP};
    private static final int[][] TRIANGLES = {{0, 1, 2}, {0, 2, 3}};

    private final float[] cornerU = new float[BakedQuad.VERTEX_COUNT];
    private final float[] cornerV = new float[BakedQuad.VERTEX_COUNT];
    private final float[] cornerDepth = new float[BakedQuad.VERTEX_COUNT];
    private final float[] textureU = new float[BakedQuad.VERTEX_COUNT];
    private final float[] textureV = new float[BakedQuad.VERTEX_COUNT];
    private final float[] depth = new float[BakedModel.FACE_TEXELS];

    public BakedModel rasterize(List<BakedQuad> quads, QuadTexels texels, IntFunction<BlockTintSource> tints) {
        int[] faces = new int[BakedModel.FACE_COUNT * BakedModel.FACE_TEXELS];
        float[] insets = new float[BakedModel.FACE_COUNT];
        int present = FaceMask.NONE;
        int occluding = FaceMask.NONE;
        int occludable = FaceMask.NONE;

        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            Arrays.fill(depth, Float.MAX_VALUE);
            int offset = face * BakedModel.FACE_TEXELS;

            for (BakedQuad quad : quads) {
                if (facing(quad, FACES[face])) {
                    paint(quad, face, faces, offset, texels);
                }
            }

            float nearest = Float.MAX_VALUE;
            float furthest = 0.0F;
            int drawn = 0;
            int opaque = 0;

            for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
                if (depth[texel] == Float.MAX_VALUE) {
                    continue;
                }

                drawn++;
                nearest = Math.min(nearest, depth[texel]);
                furthest = Math.max(furthest, depth[texel]);
                if ((faces[offset + texel] & ALPHA_MASK) == ALPHA_MASK) {
                    opaque++;
                }
            }

            if (drawn == 0) {
                insets[face] = BakedModel.EMPTY_INSET;
                continue;
            }

            insets[face] = nearest;
            present |= bit(face);
            if (furthest <= FLUSH) {
                occludable |= bit(face);
                if (opaque == BakedModel.FACE_TEXELS) {
                    occluding |= bit(face);
                }
            }
        }

        int tintLayer = tintLayer(quads);
        BlockTintSource tint = tintLayer == NO_TINT_LAYER ? null : tints.apply(tintLayer);
        int metadata = ModelMetadata.pack(present, occluding, occludable, flags(quads, tint));
        return new BakedModel(faces, insets, bounds(quads), metadata, tint);
    }

    private void paint(BakedQuad quad, int face, int[] faces, int offset, QuadTexels texels) {
        Direction normal = FACES[face];
        Direction uAxis = U_AXES[face];
        Direction vAxis = V_AXES[face];

        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            Vector3fc position = quad.position(vertex);
            cornerU[vertex] = (CENTRE + along(position, uAxis)) * BakedModel.FACE_SIDE;
            cornerV[vertex] = (CENTRE + along(position, vAxis)) * BakedModel.FACE_SIDE;
            cornerDepth[vertex] = CENTRE - along(position, normal);

            long packed = quad.packedUV(vertex);
            textureU[vertex] = UVPair.unpackU(packed);
            textureV[vertex] = UVPair.unpackV(packed);
        }

        for (int[] triangle : TRIANGLES) {
            fill(quad, triangle, faces, offset, texels);
        }
    }

    private void fill(BakedQuad quad, int[] triangle, int[] faces, int offset, QuadTexels texels) {
        int a = triangle[0];
        int b = triangle[1];
        int c = triangle[2];

        float area = edge(cornerU[a], cornerV[a], cornerU[b], cornerV[b], cornerU[c], cornerV[c]);
        if (Math.abs(area) < MIN_AREA) {
            return;
        }

        int fromU = clampToFace((int) Math.floor(Math.min(cornerU[a], Math.min(cornerU[b], cornerU[c]))));
        int toU = clampToFace((int) Math.ceil(Math.max(cornerU[a], Math.max(cornerU[b], cornerU[c]))));
        int fromV = clampToFace((int) Math.floor(Math.min(cornerV[a], Math.min(cornerV[b], cornerV[c]))));
        int toV = clampToFace((int) Math.ceil(Math.max(cornerV[a], Math.max(cornerV[b], cornerV[c]))));

        for (int v = fromV; v <= toV; v++) {
            for (int u = fromU; u <= toU; u++) {
                float pointU = u + CENTRE;
                float pointV = v + CENTRE;
                float weightA = edge(cornerU[b], cornerV[b], cornerU[c], cornerV[c], pointU, pointV) / area;
                float weightB = edge(cornerU[c], cornerV[c], cornerU[a], cornerV[a], pointU, pointV) / area;
                float weightC = 1.0F - weightA - weightB;
                if (weightA < 0.0F || weightB < 0.0F || weightC < 0.0F) {
                    continue;
                }

                int texel = v * BakedModel.FACE_SIDE + u;
                float hit = weightA * cornerDepth[a] + weightB * cornerDepth[b] + weightC * cornerDepth[c];
                if (hit >= depth[texel]) {
                    continue;
                }

                int argb = texels.argb(quad,
                        weightA * textureU[a] + weightB * textureU[b] + weightC * textureU[c],
                        weightA * textureV[a] + weightB * textureV[b] + weightC * textureV[c]);
                if ((argb & ALPHA_MASK) == 0) {
                    continue;
                }

                faces[offset + texel] = argb;
                depth[texel] = hit;
            }
        }
    }

    private static boolean facing(BakedQuad quad, Direction face) {
        Direction direction = quad.direction();
        return direction.getStepX() * face.getStepX()
                + direction.getStepY() * face.getStepY()
                + direction.getStepZ() * face.getStepZ() > 0;
    }

    private static float along(Vector3fc position, Direction axis) {
        return (position.x() - CENTRE) * axis.getStepX()
                + (position.y() - CENTRE) * axis.getStepY()
                + (position.z() - CENTRE) * axis.getStepZ();
    }

    private static float edge(float ax, float ay, float bx, float by, float pointX, float pointY) {
        return (bx - ax) * (pointY - ay) - (by - ay) * (pointX - ax);
    }

    private static int clampToFace(int coordinate) {
        return Math.clamp(coordinate, 0, BakedModel.FACE_SIDE - 1);
    }

    private static int bit(int face) {
        return 1 << face;
    }

    private static int tintLayer(List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            if (quad.materialInfo().isTinted()) {
                return quad.materialInfo().tintIndex();
            }
        }

        return NO_TINT_LAYER;
    }

    private static int flags(List<BakedQuad> quads, BlockTintSource tint) {
        int flags = tint == null ? 0 : ModelMetadata.TINTED;

        for (BakedQuad quad : quads) {
            if (quad.materialInfo().lightEmission() > 0) {
                flags |= ModelMetadata.SELF_LIT;
            }

            if (quad.materialInfo().layer().translucent()) {
                flags |= ModelMetadata.TRANSLUCENT;
            }
        }

        return flags;
    }

    private static float[] bounds(List<BakedQuad> quads) {
        float[] bounds = new float[BakedModel.BOUNDS_LENGTH];
        if (quads.isEmpty()) {
            return bounds;
        }

        Arrays.fill(bounds, BakedModel.MIN_X, BakedModel.MAX_X, Float.MAX_VALUE);
        Arrays.fill(bounds, BakedModel.MAX_X, BakedModel.BOUNDS_LENGTH, -Float.MAX_VALUE);

        for (BakedQuad quad : quads) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                Vector3fc position = quad.position(vertex);
                bounds[BakedModel.MIN_X] = Math.min(bounds[BakedModel.MIN_X], position.x());
                bounds[BakedModel.MIN_Y] = Math.min(bounds[BakedModel.MIN_Y], position.y());
                bounds[BakedModel.MIN_Z] = Math.min(bounds[BakedModel.MIN_Z], position.z());
                bounds[BakedModel.MAX_X] = Math.max(bounds[BakedModel.MAX_X], position.x());
                bounds[BakedModel.MAX_Y] = Math.max(bounds[BakedModel.MAX_Y], position.y());
                bounds[BakedModel.MAX_Z] = Math.max(bounds[BakedModel.MAX_Z], position.z());
            }
        }

        return bounds;
    }
}
