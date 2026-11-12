package com.eminus.model;

import org.joml.Vector3f;
import org.joml.Vector3fc;

final class FacePadding {
    private static final float TEXEL_CENTER = 0.5F;
    private static final int LAST_TEXEL = BakedModel.FACE_SIDE - 1;

    static void pad(int face, Vector3fc uAxis, Vector3fc vAxis, float[] bounds, int[] faces, long[] tintMask,
            int[] under) {
        Vector3fc low = new Vector3f(bounds[BakedModel.MIN_X], bounds[BakedModel.MIN_Y], bounds[BakedModel.MIN_Z]);
        Vector3fc high = new Vector3f(bounds[BakedModel.MAX_X], bounds[BakedModel.MAX_Y], bounds[BakedModel.MAX_Z]);
        int fromU = first(low, high, uAxis);
        int toU = last(low, high, uAxis);
        int fromV = first(low, high, vAxis);
        int toV = last(low, high, vAxis);
        if (fromU > toU || fromV > toV) {
            return;
        }

        int offset = face * BakedModel.FACE_TEXELS;
        for (int v = 0; v < BakedModel.FACE_SIDE; v++) {
            for (int u = 0; u < BakedModel.FACE_SIDE; u++) {
                int edgeU = Math.clamp(u, fromU, toU);
                int edgeV = Math.clamp(v, fromV, toV);
                if (edgeU == u && edgeV == v) {
                    continue;
                }

                int texel = offset + v * BakedModel.FACE_SIDE + u;
                int edge = offset + edgeV * BakedModel.FACE_SIDE + edgeU;
                faces[texel] = faces[edge];
                under[texel] = under[edge];
                BakedModel.mark(tintMask, texel, BakedModel.tinted(tintMask, edge));
            }
        }
    }

    private static int first(Vector3fc low, Vector3fc high, Vector3fc axis) {
        float from = Math.min(texelAt(low, axis), texelAt(high, axis));
        return Math.max(0, (int) Math.ceil(from - TEXEL_CENTER));
    }

    private static int last(Vector3fc low, Vector3fc high, Vector3fc axis) {
        float to = Math.max(texelAt(low, axis), texelAt(high, axis));
        return Math.min(LAST_TEXEL, (int) Math.floor(to - TEXEL_CENTER));
    }

    private static float texelAt(Vector3fc corner, Vector3fc axis) {
        return (QuadPainter.CENTER + QuadPainter.along(corner, axis)) * BakedModel.FACE_SIDE;
    }

    private FacePadding() {
    }
}
