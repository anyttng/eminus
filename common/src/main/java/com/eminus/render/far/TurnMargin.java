package com.eminus.render.far;

import com.eminus.render.tree.CameraFrame;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public final class TurnMargin {
    public static final float NO_TURN = 0.0F;

    private static final float SAME_ROTATION_DOT = 1.0F;
    private static final float QUATERNION_ANGLE_SCALE = 2.0F;

    private final Quaternionf drawnRotation = new Quaternionf();

    private boolean drawing;
    private float drawnTurn;
    private float replacedTurn;

    public void drawn(CameraFrame walkedWith) {
        replacedTurn = drawnTurn;
        drawnTurn = NO_TURN;
        drawnRotation.set(walkedWith.rotation());
        drawing = true;
    }

    public float frame(Quaternionfc rotation) {
        if (!drawing) {
            return NO_TURN;
        }

        float turned = angle(drawnRotation, rotation);
        drawnTurn = Math.max(drawnTurn, turned);
        return turned > NO_TURN ? Math.max(replacedTurn, drawnTurn) : NO_TURN;
    }

    static float angle(Quaternionfc from, Quaternionfc to) {
        if (from.equals(to)) {
            return NO_TURN;
        }

        float dot = Math.min(SAME_ROTATION_DOT, Math.abs(from.dot(to)));
        return QUATERNION_ANGLE_SCALE * (float) Math.acos(dot);
    }
}
