package com.eminus.mesh;

import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

public final class QuadGroups {
    public static final int DIRECTIONAL_COUNT = 6;
    public static final int DOUBLE_SIDED = DIRECTIONAL_COUNT;
    public static final int TRANSLUCENT = DIRECTIONAL_COUNT + 1;
    public static final int COUNT = DIRECTIONAL_COUNT + 2;

    public static int of(Direction face, int metadata) {
        if (ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT)) {
            return TRANSLUCENT;
        }

        return (ModelMetadata.present(metadata) & bit(face.getOpposite())) == 0 ? DOUBLE_SIDED : face.ordinal();
    }

    public static int ofBlade(int metadata) {
        return ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT) ? TRANSLUCENT : DOUBLE_SIDED;
    }

    private static int bit(Direction face) {
        return 1 << face.ordinal();
    }

    private QuadGroups() {
    }
}
