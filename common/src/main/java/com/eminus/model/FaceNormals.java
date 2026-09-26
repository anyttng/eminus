package com.eminus.model;

import net.minecraft.core.Direction;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class FaceNormals {
    private static final Vector3fc[] BY_FACE = normals();

    public static Vector3fc of(Direction face) {
        return BY_FACE[face.ordinal()];
    }

    private static Vector3fc[] normals() {
        Direction[] faces = Direction.values();
        Vector3fc[] normals = new Vector3fc[faces.length];
        for (Direction face : faces) {
            normals[face.ordinal()] = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        }

        return normals;
    }

    private FaceNormals() {
    }
}
