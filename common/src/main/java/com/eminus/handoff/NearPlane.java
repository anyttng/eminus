package com.eminus.handoff;

public final class NearPlane {
    public static final float BLOCKS = 16.0F;
    public static final float SHORT_BLOCKS = 8.0F;
    public static final int SHORT_RENDER_DISTANCE_CHUNKS = 2;

    public static float blocks(int renderDistanceChunks) {
        return renderDistanceChunks <= SHORT_RENDER_DISTANCE_CHUNKS ? SHORT_BLOCKS : BLOCKS;
    }

    private NearPlane() {
    }
}
