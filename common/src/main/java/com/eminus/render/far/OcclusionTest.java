package com.eminus.render.far;

import java.nio.ByteBuffer;

import com.eminus.render.arena.MeshSlot;
import com.eminus.render.backend.DepthConvention;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class OcclusionTest {
    private static final int CORNERS = 8;
    private static final float HALF = 0.5F;
    private static final float NDC_LOW = -1.0F;
    private static final float NDC_HIGH = 1.0F;
    private static final float NDC_SPAN = NDC_HIGH - NDC_LOW;
    private static final int DEPTH_MARGIN_ULPS = 16;

    private final DepthConvention depth;
    private final int blockPixels;
    private final Matrix4f viewProjection = new Matrix4f();
    private final Vector4f corner = new Vector4f();
    private float[] texels = new float[0];
    private int texelWidth;
    private int texelHeight;
    private int pixelWidth;
    private int pixelHeight;
    private int guardPixels;
    private double eyeX;
    private double eyeY;
    private double eyeZ;
    private long frame;

    public OcclusionTest(DepthConvention depth, int blockPixels) {
        this.depth = depth;
        this.blockPixels = blockPixels;
    }

    public void load(ByteBuffer farthest, int texelWidth, int texelHeight, int pixelWidth, int pixelHeight,
            int guardPixels, Matrix4fc viewProjection, double eyeX, double eyeY, double eyeZ, long frame) {
        int count = texelWidth * texelHeight;
        if (texels.length != count) {
            texels = new float[count];
        }
        for (int at = 0; at < count; at++) {
            texels[at] = farthest.getFloat(at * Float.BYTES);
        }

        this.texelWidth = texelWidth;
        this.texelHeight = texelHeight;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.guardPixels = guardPixels;
        this.viewProjection.set(viewProjection);
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;
        this.frame = frame;
    }

    public long frame() {
        return frame;
    }

    public boolean takenNear(double x, double y, double z, double blocks) {
        double dx = x - eyeX;
        double dy = y - eyeY;
        double dz = z - eyeZ;
        return dx * dx + dy * dy + dz * dz <= blocks * blocks;
    }

    public boolean hidden(float[] bounds) {
        float minX = NDC_HIGH;
        float minY = NDC_HIGH;
        float maxX = NDC_LOW;
        float maxY = NDC_LOW;
        float nearest = (float) depth.farthest();
        for (int index = 0; index < CORNERS; index++) {
            corner.set((float) (bounds[(index & 1) == 0 ? MeshSlot.MIN_X : MeshSlot.MAX_X] - eyeX),
                    (float) (bounds[(index & 2) == 0 ? MeshSlot.MIN_Y : MeshSlot.MAX_Y] - eyeY),
                    (float) (bounds[(index & 4) == 0 ? MeshSlot.MIN_Z : MeshSlot.MAX_Z] - eyeZ), 1.0F);
            viewProjection.transform(corner);
            if (corner.w <= 0.0F) {
                return false;
            }

            float x = corner.x / corner.w;
            float y = corner.y / corner.w;
            float z = windowDepth(corner.z / corner.w);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            if (nearer(z, nearest)) {
                nearest = z;
            }
        }

        if (minX < NDC_LOW || minY < NDC_LOW || maxX > NDC_HIGH || maxY > NDC_HIGH) {
            return false;
        }

        float margin = DEPTH_MARGIN_ULPS * Math.ulp(nearest);
        nearest = depth.reversed() ? nearest + margin : nearest - margin;
        float guardX = NDC_SPAN * guardPixels / pixelWidth;
        float guardY = NDC_SPAN * guardPixels / pixelHeight;
        int firstX = texel(minX - guardX, pixelWidth, texelWidth);
        int lastX = texel(maxX + guardX, pixelWidth, texelWidth);
        int firstY = texel(minY - guardY, pixelHeight, texelHeight);
        int lastY = texel(maxY + guardY, pixelHeight, texelHeight);
        for (int y = firstY; y <= lastY; y++) {
            for (int x = firstX; x <= lastX; x++) {
                if (!nearer(texels[y * texelWidth + x], nearest)) {
                    return false;
                }
            }
        }
        return true;
    }

    private int texel(float ndc, int pixels, int texelCount) {
        float clamped = Math.clamp(ndc, NDC_LOW, NDC_HIGH);
        int texel = (int) Math.floor((clamped * HALF + HALF) * pixels / blockPixels);
        return Math.clamp(texel, 0, texelCount - 1);
    }

    private float windowDepth(float ndcZ) {
        return depth.zeroToOne() ? ndcZ : ndcZ * HALF + HALF;
    }

    private boolean nearer(float a, float b) {
        return depth.reversed() ? a > b : a < b;
    }
}
