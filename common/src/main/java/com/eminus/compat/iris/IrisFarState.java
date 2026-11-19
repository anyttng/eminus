package com.eminus.compat.iris;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class IrisFarState {
    private static final int NO_TEXTURE = 0;

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f PROJECTION_INVERSE = new Matrix4f();
    private static final Matrix4f PREVIOUS_PROJECTION = new Matrix4f();
    private static final Matrix4f MERGED_PROJECTION = new Matrix4f();
    private static final Matrix4f MERGED_PROJECTION_INVERSE = new Matrix4f();
    private static final Matrix4f MERGED_PREVIOUS_PROJECTION = new Matrix4f();

    private static int depth = NO_TEXTURE;
    private static int opaqueDepth = NO_TEXTURE;
    private static int mergedDepth = NO_TEXTURE;
    private static int mergedOpaqueDepth = NO_TEXTURE;
    private static int renderDistance;
    private static float nearPlane;
    private static float farPlane;
    private static boolean distantHorizons;

    private IrisFarState() {
    }

    static void write(int depthTexture, int opaqueDepthTexture, Matrix4fc projection, Matrix4fc previousProjection,
            int renderDistanceBlocks, boolean distantHorizonsNames) {
        depth = depthTexture;
        opaqueDepth = opaqueDepthTexture;
        PROJECTION.set(projection);
        projection.invert(PROJECTION_INVERSE);
        PREVIOUS_PROJECTION.set(previousProjection);
        renderDistance = renderDistanceBlocks;
        nearPlane = projection.perspectiveNear();
        farPlane = projection.perspectiveFar();
        distantHorizons = distantHorizonsNames;
    }

    static void writeMerged(int mergedDepthTexture, int mergedOpaqueDepthTexture, Matrix4fc mergedProjection) {
        mergedDepth = mergedDepthTexture;
        mergedOpaqueDepth = mergedOpaqueDepthTexture;
        MERGED_PREVIOUS_PROJECTION.set(MERGED_PROJECTION);
        MERGED_PROJECTION.set(mergedProjection);
        mergedProjection.invert(MERGED_PROJECTION_INVERSE);
    }

    static void clear() {
        depth = NO_TEXTURE;
        opaqueDepth = NO_TEXTURE;
        mergedDepth = NO_TEXTURE;
        mergedOpaqueDepth = NO_TEXTURE;
        renderDistance = 0;
        distantHorizons = false;
    }

    public static int depth() {
        return depth;
    }

    public static int opaqueDepth() {
        return opaqueDepth;
    }

    public static int mergedDepth() {
        return mergedDepth;
    }

    public static int mergedOpaqueDepth() {
        return mergedOpaqueDepth;
    }

    public static Matrix4fc mergedProjection() {
        return MERGED_PROJECTION;
    }

    public static Matrix4fc mergedProjectionInverse() {
        return MERGED_PROJECTION_INVERSE;
    }

    public static Matrix4fc mergedPreviousProjection() {
        return MERGED_PREVIOUS_PROJECTION;
    }

    public static int renderDistance() {
        return renderDistance;
    }

    public static Matrix4fc projection() {
        return PROJECTION;
    }

    public static Matrix4fc projectionInverse() {
        return PROJECTION_INVERSE;
    }

    public static Matrix4fc previousProjection() {
        return PREVIOUS_PROJECTION;
    }

    public static float nearPlane() {
        return nearPlane;
    }

    public static float farPlane() {
        return farPlane;
    }

    public static boolean distantHorizons() {
        return distantHorizons;
    }
}
