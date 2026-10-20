package com.eminus.mesh;

import com.eminus.model.ModelMetadata;

final class FaceCubic {
    private static final float FLUSH = 1.0E-4F;

    static boolean of(MeshModels models, int stateId, int metadata, float inset) {
        return !ModelMetadata.has(metadata, ModelMetadata.SLOPED)
                && (inset < FLUSH || StateShade.has(models.shade(stateId), StateShade.FULL_COLLISION));
    }

    private FaceCubic() {
    }
}
