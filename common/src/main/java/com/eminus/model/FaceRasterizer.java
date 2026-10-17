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

    private static final float CENTER = QuadPainter.CENTER;
    private static final float FLUSH = 1.0F / 256.0F;
    private static final float MIN_FACING = 1.0E-3F;
    private static final float DIAGONAL = 0.70710677F;
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

    private record Facing(ModelQuad quad, Vector3fc normal) {
    }

    private final QuadPainter painter = new QuadPainter();
    private final FaceMip[] mips = new FaceMip[BakedModel.FACE_COUNT];
    private final Direction[] shades = new Direction[BakedModel.FACE_COUNT];
    private final Vector3f planeNormal = new Vector3f();
    private final Vector3f planePoint = new Vector3f();
    private final Vector3f[] planeNormals = vectors();
    private final Vector3f[] planeOrigins = vectors();
    private int present;
    private int occluding;
    private int occludable;
    private boolean forceOpaque;

    public BakedModel rasterize(List<ModelQuad> quads, QuadTexels texels, IntFunction<Tint> tints) {
        return rasterize(quads, texels, tints, false);
    }

    public BakedModel rasterize(List<ModelQuad> quads, QuadTexels texels, IntFunction<Tint> tints, boolean opaque) {
        forceOpaque = opaque;
        painter.opaque(opaque);
        Arrays.fill(mips, FaceMip.MEAN);
        Arrays.fill(shades, null);
        List<Facing> facings = new ArrayList<>(quads.size());
        for (ModelQuad quad : quads) {
            facings.add(new Facing(quad, normal(quad)));
        }

        return bladed(quads) ? blades(quads, facings, texels, tints) : box(quads, facings, texels, tints);
    }

    private BakedModel box(List<ModelQuad> quads, List<Facing> facings, QuadTexels texels,
            IntFunction<Tint> tints) {
        int[] faces = new int[BakedModel.FACE_COUNT * BakedModel.FACE_TEXELS];
        long[] tintMask = BakedModel.untintedMask();
        float[] insets = new float[BakedModel.FACE_COUNT];
        float[] slopes = new float[BakedModel.SLOPES_LENGTH];

        int flags = boxFaces(facings, 0, faces, tintMask, insets, slopes, texels);
        return model(quads, faces, tintMask, insets, slopes, bounds(quads), tints, flags);
    }

    private BakedModel blades(List<ModelQuad> quads, List<Facing> facings, QuadTexels texels,
            IntFunction<Tint> tints) {
        int[] faces = new int[BakedModel.FACE_COUNT * BakedModel.FACE_TEXELS];
        long[] tintMask = BakedModel.untintedMask();
        float[] insets = new float[BakedModel.FACE_COUNT];
        Arrays.fill(insets, BakedModel.EMPTY_INSET);
        float[] bounds = bounds(quads);
        float[] frame = {bounds[BakedModel.MIN_X], bounds[BakedModel.MAX_X] - bounds[BakedModel.MIN_X],
                bounds[BakedModel.MIN_Y], bounds[BakedModel.MAX_Y] - bounds[BakedModel.MIN_Y]};
        List<Facing> planes = new ArrayList<>();

        for (Facing facing : facings) {
            if (!blade(facing.normal())) {
                planes.add(facing);
            }
        }

        for (int blade = 0; blade < BLADE_COUNT; blade++) {
            painter.clear();
            int offset = blade * BakedModel.FACE_TEXELS;

            for (Facing facing : facings) {
                if (blade(facing.normal()) && facing(facing, BLADE_NORMALS[blade])) {
                    painter.paint(facing.quad(), BLADE_NORMALS[blade], BLADE_U_AXIS, BLADE_V_AXIS, frame, faces,
                            tintMask, offset, texels);
                }
            }

            insets[blade] = CENTER;
            mips[blade] = painter.mip();
            shades[blade] = painter.shade(FACES[blade]);
        }

        float[] slopes = new float[BakedModel.SLOPES_LENGTH];
        int flags = boxFaces(planes, BakedModel.FIRST_SIDE_FACE, faces, tintMask, insets, slopes, texels);
        return model(quads, faces, tintMask, insets, slopes, bounds, tints, flags | ModelMetadata.BLADED);
    }

    private int boxFaces(List<Facing> quads, int firstFace, int[] faces, long[] tintMask, float[] insets,
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
        mips[face] = FaceMip.MEAN;
        present &= ~bit(face);
    }

    private boolean slope(List<Facing> quads, int face, float[] insets, float[] slopes) {
        ModelQuad first = null;

        for (Facing quad : quads) {
            if (!facing(quad, FACE_NORMALS[face])) {
                continue;
            }

            if (first == null) {
                first = quad.quad();
                planeNormal.set(quad.normal());
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

    private boolean coplanar(Facing quad, ModelQuad first) {
        return quad.normal().dot(planeNormal) >= ALIGNED
                && Math.abs(planeNormal.dot(planePoint.set(quad.quad().corner(0)).sub(first.corner(0)))) <= COPLANAR;
    }

    @SuppressWarnings("EnumOrdinal")
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
        return CENTER - QuadPainter.along(planePoint, FACE_NORMALS[face]);
    }

    private void boxFace(List<Facing> quads, int face, int[] faces, long[] tintMask, float[] insets,
            QuadTexels texels) {
        painter.clear();
        int offset = face * BakedModel.FACE_TEXELS;

        for (Facing quad : quads) {
            if (facing(quad, FACE_NORMALS[face])) {
                painter.paint(quad.quad(), FACE_NORMALS[face], U_AXES[face], V_AXES[face], QuadPainter.UNIT_FRAME,
                        faces, tintMask, offset, texels);
            }
        }

        mips[face] = painter.mip();
        shades[face] = painter.shade(FACES[face]);

        float nearest = Float.MAX_VALUE;
        float furthest = 0.0F;
        int drawn = 0;
        int opaque = 0;

        for (int texel = 0; texel < BakedModel.FACE_TEXELS; texel++) {
            if (!painter.drawn(texel)) {
                continue;
            }

            drawn++;
            nearest = Math.min(nearest, painter.depth(texel));
            furthest = Math.max(furthest, painter.depth(texel));
            if (forceOpaque || (faces[offset + texel] & ALPHA_MASK) == ALPHA_MASK) {
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
        int tintRow = BiomeColors.NO_ROW;
        if (tint != null) {
            tint.apply(tintMask);
            tintRow = tint.row();
        }

        int metadata = ModelMetadata.pack(present, occluding, occludable, emission(quads),
                flags(quads, tintRow) | extraFlags | (forceOpaque ? ModelMetadata.OPAQUE : 0));
        return new BakedModel(faces, tintMask, insets, slopes, bounds, metadata, tintRow, mips.clone(),
                BakedModel.packShades(shades));
    }

    public static boolean bladed(List<ModelQuad> quads) {
        boolean blades = false;

        for (ModelQuad quad : quads) {
            Vector3fc normal = normal(quad);
            if (blade(normal)) {
                blades = true;
            } else if (Math.abs(normal.y()) > DIAGONAL) {
                return false;
            }
        }

        return blades;
    }

    static boolean blade(Vector3fc normal) {
        return Math.abs(normal.y()) <= MIN_FACING
                && Math.abs(Math.abs(normal.x()) - Math.abs(normal.z())) <= MIN_FACING;
    }

    private static boolean facing(Facing quad, Vector3fc normal) {
        return quad.normal().dot(normal) > MIN_FACING;
    }

    static Vector3fc normal(ModelQuad quad) {
        Vector3f normal = new Vector3f();
        GeometryUtils.normal(quad.corner(0), quad.corner(1), quad.corner(2), normal);
        return normal;
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
        int flags = tintRow == BiomeColors.NO_ROW ? 0 : ModelMetadata.TINTED;

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

    static float[] bounds(List<ModelQuad> quads) {
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
