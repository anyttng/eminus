package com.eminus.render.tree;

import org.joml.Matrix4fc;

public record CameraFrame(
        double eyeX,
        double eyeY,
        double eyeZ,
        Matrix4fc viewProjection,
        float pixelsPerBlock,
        int farCells,
        int subdivisionPixels,
        boolean arenaPressure) {
}
