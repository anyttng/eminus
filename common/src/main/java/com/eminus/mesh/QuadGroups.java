package com.eminus.mesh;

import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

public final class QuadGroups {
    public static final int DIRECTIONAL_COUNT = 6;
    public static final int DOUBLE_SIDED = DIRECTIONAL_COUNT;
    public static final int TRANSLUCENT = DIRECTIONAL_COUNT + 1;
    public static final int FIRST_BORDER = TRANSLUCENT + 1;
    public static final int COUNT = FIRST_BORDER + DIRECTIONAL_COUNT;
    public static final int NO_DIRECTION = -1;

    public static int of(Direction face, int metadata) {
        if (ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT)) {
            return TRANSLUCENT;
        }

        return (ModelMetadata.present(metadata) & bit(face.getOpposite())) == 0 ? DOUBLE_SIDED : face.ordinal();
    }

    public static int ofBlade(int metadata) {
        return ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT) ? TRANSLUCENT : DOUBLE_SIDED;
    }

    public static int border(Direction face) {
        return FIRST_BORDER + face.ordinal();
    }

    public static boolean isBorder(int group) {
        return group >= FIRST_BORDER;
    }

    public static int direction(int group) {
        if (group < DIRECTIONAL_COUNT) {
            return group;
        }

        return isBorder(group) ? group - FIRST_BORDER : NO_DIRECTION;
    }

    private static int bit(Direction face) {
        return 1 << face.ordinal();
    }

    private QuadGroups() {
    }
}
