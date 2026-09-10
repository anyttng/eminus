package com.eminus.render.far;

import com.eminus.mesh.QuadGroups;
import com.eminus.render.arena.MeshSlot;

import net.minecraft.core.Direction;

public final class GroupFacing {
    private static final Direction[] FACES = Direction.values();

    public static boolean visible(int group, float[] bounds, double cameraX, double cameraY, double cameraZ) {
        if (group >= QuadGroups.FACE_COUNT) {
            return true;
        }

        Direction face = FACES[group];
        return beyond(face.getStepX(), bounds[MeshSlot.MIN_X], bounds[MeshSlot.MAX_X], cameraX)
                && beyond(face.getStepY(), bounds[MeshSlot.MIN_Y], bounds[MeshSlot.MAX_Y], cameraY)
                && beyond(face.getStepZ(), bounds[MeshSlot.MIN_Z], bounds[MeshSlot.MAX_Z], cameraZ);
    }

    private static boolean beyond(int step, float min, float max, double camera) {
        if (step > 0) {
            return camera > min;
        }

        if (step < 0) {
            return camera < max;
        }

        return true;
    }

    private GroupFacing() {
    }
}
