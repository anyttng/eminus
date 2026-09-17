package com.eminus.model;

import java.util.List;

import com.eminus.cell.FaceMask;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.floats.FloatOpenHashSet;

import org.joml.GeometryUtils;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public record ShapeDivergence(int quads, float[] depth, float[] bounds, int planes, int tilted, int blades,
        int lostFaces, boolean bladed, float bladeScale) {
    public static final int AXES = 3;
    public static final float NO_BLADES = 1.0F;

    private static final float ALIGNED = 1.0F - 1.0E-3F;
    private static final float MIN_FACING = 1.0E-3F;
    private static final float PLANE_STEPS = 1024.0F;
    private static final int NOT_ALIGNED = -1;
    private static final Direction[] FACES = Direction.values();

    @FunctionalInterface
    public interface SpriteColumns {
        float columns(BakedQuad quad, float uSpan);
    }

    public static ShapeDivergence measure(List<BakedQuad> quads, BakedModel baked, SpriteColumns sprites) {
        float[] depth = new float[BakedModel.FACE_COUNT];
        float[] bounds = new float[AXES];
        int tilted = 0;
        int blades = 0;
        int lostFaces = FaceMask.NONE;
        float bladeScale = NO_BLADES;
        int present = ModelMetadata.present(baked.metadata());
        boolean bladed = ModelMetadata.has(baked.metadata(), ModelMetadata.BLADED);

        FloatOpenHashSet[] planes = new FloatOpenHashSet[BakedModel.FACE_COUNT];
        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            planes[face] = new FloatOpenHashSet();
        }

        Vector3f normal = new Vector3f();
        for (BakedQuad quad : quads) {
            GeometryUtils.normal(quad.position(0), quad.position(1), quad.position(2), normal);
            int face = alignedFace(normal);

            if (face != NOT_ALIGNED) {
                float quadDepth = depthOf(quad, FACES[face]);
                planes[face].add(Math.round(quadDepth * PLANE_STEPS) / PLANE_STEPS);
                if ((present & 1 << face) == 0) {
                    lostFaces |= 1 << face;
                } else {
                    depth[face] = Math.max(depth[face], Math.abs(quadDepth - baked.insets()[face]));
                }
            } else if (blade(normal)) {
                blades++;
                float scale = bladeScale(quad, sprites);
                if (Math.abs(scale - NO_BLADES) > Math.abs(bladeScale - NO_BLADES)) {
                    bladeScale = scale;
                }
            } else {
                tilted++;
            }
        }

        if (!quads.isEmpty()) {
            float[] game = boundsOf(quads);
            for (int axis = 0; axis < AXES; axis++) {
                bounds[axis] = Math.max(Math.abs(game[axis] - baked.bounds()[axis]),
                        Math.abs(game[axis + AXES] - baked.bounds()[axis + AXES]));
            }
        }

        int mostPlanes = 0;
        for (FloatOpenHashSet facePlanes : planes) {
            mostPlanes = Math.max(mostPlanes, facePlanes.size());
        }

        return new ShapeDivergence(quads.size(), depth, bounds, mostPlanes, tilted, blades, lostFaces, bladed,
                bladeScale);
    }

    public float bladeDrift() {
        return Math.abs(bladeScale - NO_BLADES);
    }

    public float deepest() {
        float deepest = 0.0F;
        for (float face : depth) {
            deepest = Math.max(deepest, face);
        }

        return deepest;
    }

    private static int alignedFace(Vector3fc normal) {
        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            if (normal.dot(FACES[face].getUnitVec3f()) > ALIGNED) {
                return face;
            }
        }

        return NOT_ALIGNED;
    }

    private static boolean blade(Vector3fc normal) {
        return Math.abs(normal.y()) <= MIN_FACING
                && Math.abs(Math.abs(normal.x()) - Math.abs(normal.z())) <= MIN_FACING;
    }

    private static float depthOf(BakedQuad quad, Direction face) {
        Vector3fc corner = quad.position(0);
        float along = (float) face.getAxis().choose(corner.x(), corner.y(), corner.z());
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0F - along : along;
    }

    private static float bladeScale(BakedQuad quad, SpriteColumns sprites) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minU = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;

        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            minX = Math.min(minX, quad.position(vertex).x());
            maxX = Math.max(maxX, quad.position(vertex).x());
            float u = UVPair.unpackU(quad.packedUV(vertex));
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
        }

        float spriteColumns = sprites.columns(quad, maxU - minU);
        return spriteColumns <= 0.0F ? NO_BLADES : (maxX - minX) * BakedModel.FACE_SIDE / spriteColumns;
    }

    private static float[] boundsOf(List<BakedQuad> quads) {
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

        for (BakedQuad quad : quads) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                Vector3fc position = quad.position(vertex);
                for (int axis = 0; axis < AXES; axis++) {
                    float value = axis == 0 ? position.x() : axis == 1 ? position.y() : position.z();
                    bounds[axis] = Math.min(bounds[axis], value);
                    bounds[axis + AXES] = Math.max(bounds[axis + AXES], value);
                }
            }
        }

        return bounds;
    }
}
