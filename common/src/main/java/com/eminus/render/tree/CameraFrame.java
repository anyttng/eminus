package com.eminus.render.tree;

import org.joml.Matrix4fc;

public record CameraFrame(
        double eyeX,
        double eyeY,
        double eyeZ,
        Matrix4fc viewProjection,
        float pixelsPerBlock,
        float inViewPixelsPerBlock,
        int farCells,
        int subdivisionPixels,
        boolean pressure) {
    public CameraFrame {
        inViewPixelsPerBlock = Math.max(inViewPixelsPerBlock, pixelsPerBlock);
    }

    CameraFrame underPressure() {
        return new CameraFrame(eyeX, eyeY, eyeZ, viewProjection, pixelsPerBlock, inViewPixelsPerBlock, farCells,
                subdivisionPixels, true);
    }
}
