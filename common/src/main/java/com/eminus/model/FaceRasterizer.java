package com.eminus.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import com.eminus.cell.FaceMask;
import com.eminus.model.port.ModelQuad;

import net.minecraft.core.Direction;

import org.joml.GeometryUtils;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class FaceRasterizer {
    public static final int BLADE_COUNT = 2;

    private static final float CENTRE = 0.5F;
    private static final float FLUSH = 1.0F / 256.0F;
    private static final float MIN_AREA = 1.0E-6F;
    private static final float MIN_FACING = 1.0E-3F;
    private static final float DIAGONAL = 0.70710678F;
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final float ALIGNED = 1.0F - 1.0E-3F;
    private static final float COPLANAR = 1.0E-4F;

    private static final Direction[] FACES = Direction.values();
    private static final Vector3fc[] FACE_NORMALS = axes(FACES);
    private static final Vector3fc[] U_AXES = axes(new Direction[] {
        Direction.EAST, Direction.EAST, Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH});
    private static final Vector3fc[] V_AXES = axes(new Direction[] {
        Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.UP, Direction.UP, Direction.UP});

    private static final Vector3fc[] BLADE_NORMALS = {
        new Vector3f(DIAGONAL, 0.0F, -DIAGONAL), new Vector3f(DIAGONAL, 0.0F, DIAGONAL)};
    private static final Vector3fc BLADE_U_AXIS = FaceNormals.of(Direction.EAST);
    private static final Vector3fc BLADE_V_AXIS = FaceNormals.of(Direction.UP);

    private static final int FRAME_U_FROM = 0;
    private static final int FRAME_U_SPAN = 1;
    private static final int FRAME_V_FROM = 2;
    private static final int FRAME_V_SPAN = 3;
    private static final float[] UNIT_FRAME = {0.0F, 1.0F, 0.0F, 1.0F};

    private static final int[][] TRIANGLES = {{0, 1, 2}, {0, 2, 3}};

    private final float[] cornerU = new float[ModelQuad.CORNERS];
    private final float[] cornerV = new float[ModelQuad.CORNERS];
    private final float[] cornerDepth = new float[ModelQuad.CORNERS];
    private final float[] textureU = new float[ModelQuad.CORNERS];
    private final float[] textureV = new float[ModelQuad.CORNERS];
    private final float[] depth = new float[BakedModel.FACE_TEXELS];
    private final Vector3f quadNormal = new Vector3f();
    private final Vector3f planeNormal = new Vector3f();
    private final Vector3f planePoint = new Vector3f();
    private final Vector3f[] planeNormals = vectors();
    private final Vector3f[] planeOrigins = vectors();
    private int present;
    private int occluding;
    private int occludable;

    public BakedModel rasterize(List<ModelQuad> quads, QuadTexels texels, IntFunction<Tint> tints) {
        return bladed(quads) ? blades(quads, texels, tints) : box(quads, texels, tints);
    }

    private BakedModel box(List<ModelQuad> quads, QuadTexels texels, IntFunction<Tint> tints) {
        int[] faces = new int[BakedModel.FACE_COUNT * BakedModel.FACE_TEXELS];
        long[] tintMask = BakedModel.untintedMask();
        float[] insets = new float[BakedModel.FACE_COUNT];
        float[] slopes = new float[BakedModel.SLOPES_LENGTH];

        int flags = boxFaces(quads, 0, faces, tintMask, insets, slopes, texels);
        return model(quads, faces, tintMask, insets, slopes, bounds(quads), tints, flags);
    }

    private BakedModel blades(List<ModelQuad> quads, QuadTexels texels, IntFunction<Tint> tints) {
        int[] faces = new int[BakedModel.FACE_COUNT * BakedModel.FACE_TEXELS];
        long[] tintMask = BakedModel.untintedMask();
        float[] insets = new float[BakedModel.FACE_COUNT];
        Arrays.fill(insets, BakedModel.EMPTY_INSET);
        float[] bounds = bounds(quads);
        float[] frame = {bounds[BakedModel.MIN_X], bounds[BakedModel.MAX_X] - bounds[BakedModel.MIN_X],
                bounds[BakedModel.MIN_Y], bounds[BakedModel.MAX_Y] - bounds[BakedModel.MIN_Y]};
        List<ModelQuad> planes = new ArrayList<>();

        for (ModelQuad quad : quads) {
            if (!blade(quad)) {
                planes.add(quad);
            }
        }

        for (int blade = 0; blade < BLADE_COUNT; blade++) {
            Arrays.fill(depth, Float.MAX_VALUE);
            int offset = blade * BakedModel.FACE_TEXELS;

            for (ModelQuad quad : quads) {
                if (blade(quad) && facing(quad, BLADE_NORMALS[blade])) {
                    paint(quad, BLADE_NORMALS[blade], BLADE_U_AXIS, BLADE_V_AXIS, frame, faces, tintMask, offset,
                            texels);
                }
            }

            insets[blade] = CENTRE;
        }

        float[] slopes = new float[BakedModel.SLOPES_LENGTH];
        int flags = boxFaces(planes, BakedModel.FIRST_SIDE_FACE, faces, tintMask, insets, slopes, texels);
        return model(quads, faces, tintMask, insets, slopes, bounds, tints, flags | ModelMetadata.BLADED);
    }

    private int boxFaces(List<ModelQuad> quads, int firstFace, int[] faces, long[] tintMask, float[] insets,
            float[] slopes, QuadTexels texels) {
        clearMasks();
        int sloped = FaceMask.NONE;

        for (int face = firstFace; face < BakedModel.FACE_COUNT; face++) {
            boxFace(quads, face, faces, tintMask, insets, texels);
            if ((present & bit(face)) != 0 && slope(quads, face, insets, slopes)) {
                sloped |= bit(face);
            }
        }

        dropRepeatedPlanes(sloped, faces, tintMask, insets, slopes);
        return sloped == FaceMask.NONE ? 0 : ModelMetadata.SLOPED;
    }

    private void dropRepeatedPlanes(int sloped, int[] faces, long[] tintMask, float[] insets, float[] slopes) {
        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            for (int other = face + 1; other < BakedModel.FACE_COUNT; other++) {
                if ((sloped & present & bit(face)) == 0 || (sloped & present & bit(other)) == 0
                        || !samePlane(face, other)) {
                    continue;
                }

                boolean otherLeansMore = planeNormals[face].dot(FACE_NORMALS[other])
                        > planeNormals[face].dot(FACE_NORMALS[face]) + MIN_FACING;
                drop(otherLeansMore ? face : other, faces, tintMask, insets, slopes);
            }
        }
    }

    private boolean samePlane(int face, int other) {
        return planeNormals[face].dot(planeNormals[other]) >= ALIGNED
                && Math.abs(planeNormals[face].dot(planePoint.set(planeOrigins[other]).sub(planeOrigins[face])))
                        <= COPLANAR;
    }

    private void drop(int face, int[] faces, long[] tintMask, float[] insets, float[] slopes) {
        int offset = face * BakedModel.FACE_TEXELS;
        for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
            faces[offset + texel] = 0;
            BakedModel.mark(tintMask, offset + texel, false);
        }

        int index = BakedModel.slopeIndex(face);
        slopes[index] = 0.0F;
        slopes[index + 1] = 0.0F;
        insets[face] = BakedModel.EMPTY_INSET;
        present &= ~bit(face);
    }

    private boolean slope(List<ModelQuad> quads, int face, float[] insets, float[] slopes) {
        ModelQuad first = null;

        for (ModelQuad quad : quads) {
            if (!facing(quad, FACE_NORMALS[face])) {
                continue;
            }

            if (first == null) {
                first = quad;
                planeNormal.set(normalOf(quad));
            } else if (!coplanar(quad, first)) {
                return false;
            }
        }

        if (first == null) {
            return false;
        }

        float atOrigin = depthOnPlane(face, first.corner(0), 0.0F, 0.0F);
        float alongWidth = depthOnPlane(face, first.corner(0), 1.0F, 0.0F) - atOrigin;
        float alongHeight = depthOnPlane(face, first.corner(0), 0.0F, 1.0F) - atOrigin;
        if (Math.abs(alongWidth) <= COPLANAR && Math.abs(alongHeight) <= COPLANAR) {
            return false;
        }

        int index = BakedModel.slopeIndex(face);
        insets[face] = atOrigin;
        slopes[index] = alongWidth;
        slopes[index + 1] = alongHeight;
        planeNormals[face].set(planeNormal);
        planeOrigins[face].set(first.corner(0));
        return true;
    }

    private boolean coplanar(ModelQuad quad, ModelQuad first) {
        return normalOf(quad).dot(planeNormal) >= ALIGNED
                && Math.abs(planeNormal.dot(planePoint.set(quad.corner(0)).sub(first.corner(0)))) <= COPLANAR;
    }

    private float depthOnPlane(int face, Vector3fc origin, float width, float height) {
        Direction.Axis normal = FACES[face].getAxis();
        int normalAxis = normal.ordinal();
        int widthAxis = (normal == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X).ordinal();
        int heightAxis = (normal == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y).ordinal();

        float across = planeNormal.get(widthAxis) * (width - origin.get(widthAxis))
                + planeNormal.get(heightAxis) * (height - origin.get(heightAxis));
        planePoint.setComponent(widthAxis, width);
        planePoint.setComponent(heightAxis, height);
        planePoint.setComponent(normalAxis, origin.get(normalAxis) - across / planeNormal.get(normalAxis));
        return CENTRE - along(planePoint, FACE_NORMALS[face]);
    }

    private void boxFace(List<ModelQuad> quads, int face, int[] faces, long[] tintMask, float[] insets,
            QuadTexels texels) {
        Arrays.fill(depth, Float.MAX_VALUE);
        int offset = face * BakedModel.FACE_TEXELS;

        for (ModelQuad quad : quads) {
            if (facing(quad, FACE_NORMALS[face])) {
                paint(quad, FACE_NORMALS[face], U_AXES[face], V_AXES[face], UNIT_FRAME, faces, tintMask, offset,
                        texels);
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
            return;
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

    private void clearMasks() {
        present = FaceMask.NONE;
        occluding = FaceMask.NONE;
        occludable = FaceMask.NONE;
    }

    private BakedModel model(List<ModelQuad> quads, int[] faces, long[] tintMask, float[] insets, float[] slopes,
            float[] bounds, IntFunction<Tint> tints, int extraFlags) {
        int tintLayer = tintLayer(quads);
        Tint tint = tintLayer == ModelQuad.NO_TINT ? null : tints.apply(tintLayer);
        int tintRow = BiomeColours.NO_ROW;
        if (tint != null) {
            tint.apply(faces, tintMask);
            tintRow = tint.row();
        }

        int metadata = ModelMetadata.pack(present, occluding, occludable, emission(quads),
                flags(quads, tintRow) | extraFlags);
        return new BakedModel(faces, tintMask, insets, slopes, bounds, metadata, tintRow);
    }

    private boolean bladed(List<ModelQuad> quads) {
        boolean blades = false;

        for (ModelQuad quad : quads) {
            if (blade(quad)) {
                blades = true;
            } else if (Math.abs(normalOf(quad).y()) > DIAGONAL) {
                return false;
            }
        }

        return blades;
    }

    private boolean blade(ModelQuad quad) {
        Vector3fc normal = normalOf(quad);
        return Math.abs(normal.y()) <= MIN_FACING
                && Math.abs(Math.abs(normal.x()) - Math.abs(normal.z())) <= MIN_FACING;
    }

    private void paint(ModelQuad quad, Vector3fc normal, Vector3fc uAxis, Vector3fc vAxis, float[] frame,
            int[] faces, long[] tintMask, int offset, QuadTexels texels) {
        for (int vertex = 0; vertex < ModelQuad.CORNERS; vertex++) {
            Vector3fc position = quad.corner(vertex);
            cornerU[vertex] = (CENTRE + along(position, uAxis) - frame[FRAME_U_FROM]) / frame[FRAME_U_SPAN]
                    * BakedModel.FACE_SIDE;
            cornerV[vertex] = (CENTRE + along(position, vAxis) - frame[FRAME_V_FROM]) / frame[FRAME_V_SPAN]
                    * BakedModel.FACE_SIDE;
            cornerDepth[vertex] = CENTRE - along(position, normal);

            textureU[vertex] = quad.u(vertex);
            textureV[vertex] = quad.v(vertex);
        }

        for (int[] triangle : TRIANGLES) {
            fill(quad, triangle, faces, tintMask, offset, texels);
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
                if (hit > depth[texel]) {
                    continue;
                }

                int argb = texels.argb(quad,
                        weightA * textureU[a] + weightB * textureU[b] + weightC * textureU[c],
                        weightA * textureV[a] + weightB * textureV[b] + weightC * textureV[c]);
                if ((argb & ALPHA_MASK) == 0) {
                    continue;
                }

                faces[offset + texel] = argb;
                BakedModel.mark(tintMask, offset + texel, tinted);
                depth[texel] = hit;
            }
        }
    }

    private boolean facing(ModelQuad quad, Vector3fc normal) {
        return normalOf(quad).dot(normal) > MIN_FACING;
    }

    private Vector3fc normalOf(ModelQuad quad) {
        GeometryUtils.normal(quad.corner(0), quad.corner(1), quad.corner(2), quadNormal);
        return quadNormal;
    }

    private static Vector3fc[] axes(Direction[] directions) {
        Vector3fc[] axes = new Vector3fc[directions.length];
        for (int index = 0; index < directions.length; index++) {
            axes[index] = FaceNormals.of(directions[index]);
        }

        return axes;
    }

    private static Vector3f[] vectors() {
        Vector3f[] vectors = new Vector3f[BakedModel.FACE_COUNT];
        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            vectors[face] = new Vector3f();
        }

        return vectors;
    }

    private static float along(Vector3fc position, Vector3fc axis) {
        return (position.x() - CENTRE) * axis.x()
                + (position.y() - CENTRE) * axis.y()
                + (position.z() - CENTRE) * axis.z();
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

    private static int tintLayer(List<ModelQuad> quads) {
        for (ModelQuad quad : quads) {
            if (quad.tinted()) {
                return quad.tintLayer();
            }
        }

        return ModelQuad.NO_TINT;
    }

    private static int flags(List<ModelQuad> quads, int tintRow) {
        int flags = tintRow == BiomeColours.NO_ROW ? 0 : ModelMetadata.TINTED;

        for (ModelQuad quad : quads) {
            if (quad.translucent()) {
                flags |= ModelMetadata.TRANSLUCENT;
            }
        }

        return flags;
    }

    private static int emission(List<ModelQuad> quads) {
        int emission = 0;

        for (ModelQuad quad : quads) {
            emission = Math.max(emission, quad.emission());
        }

        return Math.min(emission, ModelMetadata.MAX_EMISSION);
    }

    private static float[] bounds(List<ModelQuad> quads) {
        float[] bounds = new float[BakedModel.BOUNDS_LENGTH];
        if (quads.isEmpty()) {
            return bounds;
        }

        Arrays.fill(bounds, BakedModel.MIN_X, BakedModel.MAX_X, Float.MAX_VALUE);
        Arrays.fill(bounds, BakedModel.MAX_X, BakedModel.BOUNDS_LENGTH, -Float.MAX_VALUE);

        for (ModelQuad quad : quads) {
            for (int vertex = 0; vertex < ModelQuad.CORNERS; vertex++) {
                Vector3fc position = quad.corner(vertex);
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
