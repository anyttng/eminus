package com.eminus.client.frame;

import org.joml.Matrix4fc;

public record GameFrame(
        double eyeX,
        double eyeY,
        double eyeZ,
        Matrix4fc viewRotation,
        float fov,
        boolean cameraInAir,
        GameFog fog,
        int renderDistance,
        long sectionFadeMillis,
        FaceShade shade) {
}
