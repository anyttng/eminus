package com.eminus.model;

import java.util.ArrayList;
import java.util.List;

import com.eminus.cell.FaceMask;
import com.eminus.model.port.ModelQuad;

import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.floats.FloatOpenHashSet;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("ArrayRecordComponent")
public record ShapeDivergence(int quads, float[] depth, float[] bounds, int planes, int tilted, int blades,
        int lostFaces, boolean bladed, float bladeScale, int looseBlades, float bladeOffset) {
    public static final int AXES = 3;
    public static final float NO_BLADES = 1.0F;

    private static final float ALIGNED = 1.0F - 1.0E-3F;
    private static final float PLANE_STEPS = 1024.0F;
    private static final int NOT_ALIGNED = -1;
    private static final float NO_FACE = -1.0F;
    private static final float HALF = 0.5F;
    private static final Direction[] FACES = Direction.values();

    @FunctionalInterface
    public interface SpriteColumns {
        float columns(ModelQuad quad, float uSpan);
    }

    public static ShapeDivergence measure(List<ModelQuad> quads, BakedModel baked, SpriteColumns sprites) {
        List<BakedModel> drawn = new ArrayList<>(baked.pieces().size() + 1);
        drawn.add(baked);
        drawn.addAll(baked.pieces());

        float[] depth = new float[BakedModel.FACE_COUNT];
        int tilted = 0;
        int blades = 0;
        int looseBlades = 0;
        float bladeOffset = 0.0F;
        int lostFaces = FaceMask.NONE;
        float bladeScale = NO_BLADES;
        boolean bladed = ModelMetadata.has(baked.metadata(), ModelMetadata.BLADED);

        FloatOpenHashSet[] planes = new FloatOpenHashSet[BakedModel.FACE_COUNT];
        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            planes[face] = new FloatOpenHashSet();
        }

        for (ModelQuad quad : quads) {
            Vector3fc normal = FaceRasterizer.normal(quad);
            int face = alignedFace(normal);

            if (face != NOT_ALIGNED) {
                float quadDepth = depthOf(quad, FACES[face]);
                planes[face].add(Math.round(quadDepth * PLANE_STEPS) / PLANE_STEPS);
                float nearest = nearestInset(drawn, face, quadDepth);
                if (nearest == NO_FACE) {
                    lostFaces |= 1 << face;
                } else {
                    depth[face] = Math.max(depth[face], nearest);
                }
            } else if (FaceRasterizer.blade(normal)) {
                blades++;
                BakedModel holder = bladeHolder(drawn, quad);
                if (holder == null) {
                    looseBlades++;
                    continue;
                }

                bladeOffset = Math.max(bladeOffset, axisOffset(quad, holder.bounds()));
                float scale = bladeScale(quad, holder.bounds(), sprites);
                if (Math.abs(scale - NO_BLADES) > Math.abs(bladeScale - NO_BLADES)) {
                    bladeScale = scale;
                }
            } else {
                tilted++;
            }
        }

        return new ShapeDivergence(quads.size(), depth, boundsDrift(quads, drawn), mostPlanes(planes), tilted,
                blades, lostFaces, bladed, bladeScale, looseBlades, bladeOffset);
    }

    private static float nearestInset(List<BakedModel> drawn, int face, float quadDepth) {
        float nearest = NO_FACE;
        for (BakedModel model : drawn) {
            if ((ModelMetadata.present(model.metadata()) & 1 << face) != 0) {
                float off = Math.abs(quadDepth - model.insets()[face]);
                nearest = nearest == NO_FACE ? off : Math.min(nearest, off);
            }
        }

        return nearest;
    }

    private static @Nullable BakedModel bladeHolder(List<BakedModel> drawn, ModelQuad quad) {
        BakedModel holder = null;
        for (BakedModel model : drawn) {
            if (ModelMetadata.has(model.metadata(), ModelMetadata.BLADED)
                    && (holder == null || axisOffset(quad, model.bounds()) < axisOffset(quad, holder.bounds()))) {
                holder = model;
            }
        }

        return holder;
    }

    private static float axisOffset(ModelQuad quad, float[] bounds) {
        float x = 0.0F;
        float z = 0.0F;
        for (int corner = 0; corner < ModelQuad.CORNERS; corner++) {
            x += quad.corner(corner).x();
            z += quad.corner(corner).z();
        }

        float alongX = x / ModelQuad.CORNERS - (bounds[BakedModel.MIN_X] + bounds[BakedModel.MAX_X]) * HALF;
        float alongZ = z / ModelQuad.CORNERS - (bounds[BakedModel.MIN_Z] + bounds[BakedModel.MAX_Z]) * HALF;
        return (float) Math.sqrt(alongX * alongX + alongZ * alongZ);
    }

    private static float[] boundsDrift(List<ModelQuad> quads, List<BakedModel> drawn) {
        float[] drift = new float[AXES];
        if (!quads.isEmpty()) {
            float[] game = FaceRasterizer.bounds(quads);
            float[] baked = unionBounds(drawn);
            for (int axis = 0; axis < AXES; axis++) {
                drift[axis] = Math.max(Math.abs(game[axis] - baked[axis]),
                        Math.abs(game[axis + AXES] - baked[axis + AXES]));
            }
        }

        return drift;
    }

    private static float[] unionBounds(List<BakedModel> drawn) {
        float[] union = drawn.getFirst().bounds().clone();
        for (BakedModel model : drawn.subList(1, drawn.size())) {
            for (int axis = 0; axis < AXES; axis++) {
                union[axis] = Math.min(union[axis], model.bounds()[axis]);
                union[axis + AXES] = Math.max(union[axis + AXES], model.bounds()[axis + AXES]);
            }
        }

        return union;
    }

    private static int mostPlanes(FloatOpenHashSet[] planes) {
        int mostPlanes = 0;
        for (FloatOpenHashSet facePlanes : planes) {
            mostPlanes = Math.max(mostPlanes, facePlanes.size());
        }

        return mostPlanes;
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
            if (normal.dot(FaceNormals.of(FACES[face])) > ALIGNED) {
                return face;
            }
        }

        return NOT_ALIGNED;
    }

    private static float depthOf(ModelQuad quad, Direction face) {
        Vector3fc corner = quad.corner(0);
        float along = (float) face.getAxis().choose(corner.x(), corner.y(), corner.z());
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0F - along : along;
    }

    private static float bladeScale(ModelQuad quad, float[] bakedBounds, SpriteColumns sprites) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minU = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;

        for (int vertex = 0; vertex < ModelQuad.CORNERS; vertex++) {
            minX = Math.min(minX, quad.corner(vertex).x());
            maxX = Math.max(maxX, quad.corner(vertex).x());
            float u = quad.u(vertex);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
        }

        float spriteColumns = sprites.columns(quad, maxU - minU);
        float imageSpan = bakedBounds[BakedModel.MAX_X] - bakedBounds[BakedModel.MIN_X];
        return spriteColumns <= 0.0F || imageSpan <= 0.0F ? NO_BLADES
                : (maxX - minX) / imageSpan * BakedModel.FACE_SIDE / spriteColumns;
    }
}
