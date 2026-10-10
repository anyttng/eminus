package com.eminus.render.far;

import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public final class ShadowCasterVolume {
    private static final int SIDES = 4;
    private static final int[] SIDE_PLANES = {Matrix4fc.PLANE_NX, Matrix4fc.PLANE_NY, Matrix4fc.PLANE_PX,
            Matrix4fc.PLANE_PY};
    private static final int MAX_PLANES = SIDES * 2;
    private static final int PLANE_FLOATS = 4;
    private static final float PARALLEL = 1.0E-6F;
    // A pack's shadow lookup reaches past its receiver: normal bias, blocker search and a texel at the map's edge.
    private static final float SAMPLE_REACH = 4.0F;

    private final Vector3f[] sideNormals = new Vector3f[SIDES];
    private final Vector3f[] edges = new Vector3f[SIDES];
    private final float[] offsets = new float[SIDES];
    private final float[] planes = new float[MAX_PLANES * PLANE_FLOATS];
    private final Vector4f plane = new Vector4f();
    private final Vector3f apex = new Vector3f();
    private final Vector3f forward = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private final Matrix3f solve = new Matrix3f();

    private int planeCount;

    public ShadowCasterVolume() {
        for (int side = 0; side < SIDES; side++) {
            sideNormals[side] = new Vector3f();
            edges[side] = new Vector3f();
        }
    }

    public ShadowCasterVolume set(Matrix4fc cameraViewProjection, Vector3fc lightTravel) {
        for (int side = 0; side < SIDES; side++) {
            cameraViewProjection.frustumPlane(SIDE_PLANES[side], plane);
            sideNormals[side].set(plane.x, plane.y, plane.z);
            offsets[side] = plane.w;
        }

        solve.setRow(0, sideNormals[0]).setRow(1, sideNormals[1]).setRow(2, sideNormals[2]).invert()
                .transform(apex.set(-offsets[0], -offsets[1], -offsets[2]));

        forward.zero();
        for (int side = 0; side < SIDES; side++) {
            Vector3f edge = sideNormals[side].cross(sideNormals[(side + 1) % SIDES], edges[side]);
            if (sideNormals[(side + 2) % SIDES].dot(edge) < 0.0F) {
                edge.negate();
            }
            forward.add(edge.normalize());
        }

        planeCount = 0;
        for (int side = 0; side < SIDES; side++) {
            if (bounds(side, lightTravel)) {
                add(sideNormals[side], offsets[side]);
            }

            int next = (side + 1) % SIDES;
            if (bounds(side, lightTravel) != bounds(next, lightTravel)) {
                edges[side].cross(lightTravel, normal);
                if (normal.lengthSquared() > PARALLEL) {
                    if (normal.dot(forward) < 0.0F) {
                        normal.negate();
                    }
                    add(normal, -normal.dot(apex));
                }
            }
        }
        return this;
    }

    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (int index = 0; index < planeCount * PLANE_FLOATS; index += PLANE_FLOATS) {
            float a = planes[index];
            float b = planes[index + 1];
            float c = planes[index + 2];
            if (a * (a > 0.0F ? maxX + SAMPLE_REACH : minX - SAMPLE_REACH)
                    + b * (b > 0.0F ? maxY + SAMPLE_REACH : minY - SAMPLE_REACH)
                    + c * (c > 0.0F ? maxZ + SAMPLE_REACH : minZ - SAMPLE_REACH) + planes[index + 3] < 0.0F) {
                return false;
            }
        }
        return true;
    }

    private boolean bounds(int side, Vector3fc lightTravel) {
        return sideNormals[side].dot(lightTravel) <= 0.0F;
    }

    private void add(Vector3fc inward, float offset) {
        int index = planeCount * PLANE_FLOATS;
        planes[index] = inward.x();
        planes[index + 1] = inward.y();
        planes[index + 2] = inward.z();
        planes[index + 3] = offset;
        planeCount++;
    }
}
