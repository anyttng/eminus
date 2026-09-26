package com.eminus.model.port;

import net.minecraft.core.Direction;

import org.joml.Vector3fc;

public record ModelQuad(Vector3fc[] corners, float[] uvs, Sprite sprite, int tintLayer, boolean translucent,
        int emission, Direction face) {
    public static final int CORNERS = 4;
    public static final int NO_TINT = -1;

    public Vector3fc corner(int corner) {
        return corners[corner];
    }

    public float u(int corner) {
        return uvs[corner * 2];
    }

    public float v(int corner) {
        return uvs[corner * 2 + 1];
    }

    public boolean tinted() {
        return tintLayer != NO_TINT;
    }
}
