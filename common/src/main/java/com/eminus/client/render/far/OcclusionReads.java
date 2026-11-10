package com.eminus.client.render.far;

import java.util.ArrayDeque;
import java.util.Deque;

import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Readback;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.backend.DepthConvention;
import com.eminus.render.far.OcclusionTest;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

final class OcclusionReads implements AutoCloseable {
    static final int OWN_DEPTH_GUARD_PIXELS = 0;
    static final int OCCLUDER_GUARD_PIXELS = 1;
    private static final long MAX_AGE_FRAMES = 3L;
    private static final double MAX_EYE_SHIFT_BLOCKS = 2.0;
    private static final String LABEL = "eminus-occlusion-read";
    private static final int BASE_MIP = 0;
    private static final int ORIGIN = 0;

    private record Requested(long sequence, long frame, Matrix4f viewProjection, double eyeX, double eyeY,
            double eyeZ, int pixelWidth, int pixelHeight, int guardPixels) {
    }

    private final Gpu gpu;
    private final OcclusionTest test;
    private final Deque<Requested> requested = new ArrayDeque<>();
    private @Nullable Readback readback;
    private int readbackBytes;
    private boolean loaded;

    OcclusionReads(Gpu gpu, DepthConvention depth) {
        this.gpu = gpu;
        this.test = new OcclusionTest(depth, OcclusionPyramid.READ_BLOCK);
    }

    void request(Texture level, int pixelWidth, int pixelHeight, int guardPixels, Matrix4fc viewProjection,
            double eyeX, double eyeY, double eyeZ, long frame) {
        int bytes = level.width() * level.height() * level.format().bytesPerTexel();
        Readback slots = readback;
        if (slots == null || bytes > readbackBytes) {
            if (slots != null) {
                slots.close();
            }
            requested.clear();
            slots = gpu.readback(LABEL, bytes);
            readback = slots;
            readbackBytes = bytes;
        }

        long sequence = slots.request(level, BASE_MIP, ORIGIN, ORIGIN, level.width(), level.height());
        if (sequence != Readback.REFUSED) {
            requested.addLast(new Requested(sequence, frame, new Matrix4f(viewProjection), eyeX, eyeY, eyeZ,
                    pixelWidth, pixelHeight, guardPixels));
        }
    }

    @Nullable OcclusionTest current(long frame, double eyeX, double eyeY, double eyeZ) {
        Readback slots = readback;
        if (slots != null) {
            slots.poll(this::take);
        }
        return loaded && frame - test.frame() <= MAX_AGE_FRAMES
                && test.takenNear(eyeX, eyeY, eyeZ, MAX_EYE_SHIFT_BLOCKS) ? test : null;
    }

    private void take(Readback.Read read) {
        Requested match = null;
        while (!requested.isEmpty() && requested.peekFirst().sequence() <= read.sequence()) {
            Requested oldest = requested.pollFirst();
            if (oldest.sequence() == read.sequence()) {
                match = oldest;
            }
        }

        if (match != null) {
            test.load(read.texels(), read.width(), read.height(), match.pixelWidth(), match.pixelHeight(),
                    match.guardPixels(), match.viewProjection(), match.eyeX(), match.eyeY(), match.eyeZ(), match.frame());
            loaded = true;
        }
    }

    @Override
    public void close() {
        Readback slots = readback;
        if (slots != null) {
            slots.close();
            readback = null;
        }
        requested.clear();
        loaded = false;
    }
}
