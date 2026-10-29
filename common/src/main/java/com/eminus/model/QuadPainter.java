package com.eminus.model;

import java.util.Arrays;

import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.Sprite;

import net.minecraft.core.Direction;

import org.joml.Vector3fc;

final class QuadPainter {
    static final float CENTER = 0.5F;
    static final float[] UNIT_FRAME = {0.0F, 1.0F, 0.0F, 1.0F};

    private static final float MIN_AREA = 1.0E-6F;
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int FRAME_U_FROM = 0;
    private static final int FRAME_U_SPAN = 1;
    private static final int FRAME_V_FROM = 2;
    private static final int FRAME_V_SPAN = 3;
    private static final int[][] TRIANGLES = {{0, 1, 2}, {0, 2, 3}};
    private static final int DIRECTIONS = Direction.values().length;

    private final float[] cornerU = new float[ModelQuad.CORNERS];
    private final float[] cornerV = new float[ModelQuad.CORNERS];
    private final float[] cornerDepth = new float[ModelQuad.CORNERS];
    private final float[] textureU = new float[ModelQuad.CORNERS];
    private final float[] textureV = new float[ModelQuad.CORNERS];
    private final float[] depth = new float[BakedModel.FACE_TEXELS];
    private final Sprite[] sprites = new Sprite[BakedModel.FACE_TEXELS];
    private final Direction[] shades = new Direction[BakedModel.FACE_TEXELS];
    private final int[] shadeTexels = new int[DIRECTIONS];
    private boolean forceOpaque;

    void opaque(boolean opaque) {
        forceOpaque = opaque;
    }

    void clear() {
        Arrays.fill(depth, Float.MAX_VALUE);
        Arrays.fill(sprites, null);
        Arrays.fill(shades, null);
    }

    boolean drawn(int texel) {
        return depth[texel] != Float.MAX_VALUE;
    }

    float depth(int texel) {
        return depth[texel];
    }

    FaceMip mip() {
        return FaceMip.drawnBy(sprites);
    }

    Direction shade(Direction own) {
        Arrays.fill(shadeTexels, 0);
        Direction widest = own;
        int widestTexels = 0;

        for (Direction shade : shades) {
            if (shade == null) {
                continue;
            }

            int texels = ++shadeTexels[shade.get3DDataValue()];
            if (texels > widestTexels) {
                widest = shade;
                widestTexels = texels;
            }
        }

        return widest;
    }

    void paint(ModelQuad quad, Vector3fc normal, Vector3fc uAxis, Vector3fc vAxis, float[] frame, int[] faces,
            long[] tintMask, int offset, QuadTexels texels) {
        project(quad, normal, uAxis, vAxis, frame);
        for (int[] triangle : TRIANGLES) {
            fill(quad, triangle, faces, tintMask, offset, texels);
        }
    }

    private void project(ModelQuad quad, Vector3fc normal, Vector3fc uAxis, Vector3fc vAxis, float[] frame) {
        for (int vertex = 0; vertex < ModelQuad.CORNERS; vertex++) {
            Vector3fc position = quad.corner(vertex);
            cornerU[vertex] = (CENTER + along(position, uAxis) - frame[FRAME_U_FROM]) / frame[FRAME_U_SPAN]
                    * BakedModel.FACE_SIDE;
            cornerV[vertex] = (CENTER + along(position, vAxis) - frame[FRAME_V_FROM]) / frame[FRAME_V_SPAN]
                    * BakedModel.FACE_SIDE;
            cornerDepth[vertex] = CENTER - along(position, normal);

            textureU[vertex] = quad.u(vertex);
            textureV[vertex] = quad.v(vertex);
        }
    }

    private void fill(ModelQuad quad, int[] triangle, int[] faces, long[] tintMask, int offset, QuadTexels texels) {
        int a = triangle[0];
        int b = triangle[1];
        int c = triangle[2];

        float area = edge(cornerU[a], cornerV[a], cornerU[b], cornerV[b], cornerU[c], cornerV[c]);
        if (Math.abs(area) < MIN_AREA) {
            return;
        }

        boolean tinted = quad.tinted();
        int fromU = clampToFace((int) Math.floor(Math.min(cornerU[a], Math.min(cornerU[b], cornerU[c]))));
        int toU = clampToFace((int) Math.ceil(Math.max(cornerU[a], Math.max(cornerU[b], cornerU[c]))));
        int fromV = clampToFace((int) Math.floor(Math.min(cornerV[a], Math.min(cornerV[b], cornerV[c]))));
        int toV = clampToFace((int) Math.ceil(Math.max(cornerV[a], Math.max(cornerV[b], cornerV[c]))));

        for (int v = fromV; v <= toV; v++) {
            for (int u = fromU; u <= toU; u++) {
                float pointU = u + CENTER;
                float pointV = v + CENTER;
                float weightA = edge(cornerU[b], cornerV[b], cornerU[c], cornerV[c], pointU, pointV) / area;
                float weightB = edge(cornerU[c], cornerV[c], cornerU[a], cornerV[a], pointU, pointV) / area;
                float weightC = 1.0F - weightA - weightB;
                if (weightA < 0.0F || weightB < 0.0F || weightC < 0.0F) {
                    continue;
                }

                int texel = v * BakedModel.FACE_SIDE + u;
                float hit = weightA * cornerDepth[a] + weightB * cornerDepth[b] + weightC * cornerDepth[c];
                if (hit > depth[texel]) {
                    continue;
                }

                int argb = texels.argb(quad,
                        weightA * textureU[a] + weightB * textureU[b] + weightC * textureU[c],
                        weightA * textureV[a] + weightB * textureV[b] + weightC * textureV[c]);
                if (!forceOpaque && (argb & ALPHA_MASK) == 0) {
                    continue;
                }

                faces[offset + texel] = argb;
                sprites[texel] = quad.sprite();
                shades[texel] = quad.shade();
                BakedModel.mark(tintMask, offset + texel, tinted);
                depth[texel] = hit;
            }
        }
    }

    static float along(Vector3fc position, Vector3fc axis) {
        return (position.x() - CENTER) * axis.x()
                + (position.y() - CENTER) * axis.y()
                + (position.z() - CENTER) * axis.z();
    }

    private static float edge(float ax, float ay, float bx, float by, float pointX, float pointY) {
        return (bx - ax) * (pointY - ay) - (by - ay) * (pointX - ax);
    }

    private static int clampToFace(int coordinate) {
        return Math.clamp(coordinate, 0, BakedModel.FACE_SIDE - 1);
    }
}
