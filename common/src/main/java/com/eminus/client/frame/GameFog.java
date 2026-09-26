package com.eminus.client.frame;

import org.joml.Vector4fc;

public record GameFog(float environmentalStart, float environmentalEnd, float renderDistanceStart,
        float renderDistanceEnd, Vector4fc colour) {
}
