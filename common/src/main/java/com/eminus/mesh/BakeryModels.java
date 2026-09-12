package com.eminus.mesh;

import com.eminus.model.BakedModel;
import com.eminus.model.ModelBakery;
import com.eminus.model.ModelIndex;
import com.eminus.model.ModelSource;

public record BakeryModels(ModelIndex index, ModelSource models) implements MeshModels {
    private static final int NO_METADATA = 0;

    @Override
    public int modelId(int stateId, Runnable whenBaked) {
        int modelId = index.modelId(stateId, whenBaked);
        return modelId == ModelBakery.MISSING ? MISSING : modelId;
    }

    @Override
    public int fluidModelId(int stateId, Runnable whenBaked) {
        int fluidId = index.fluidModelId(stateId, whenBaked);
        if (fluidId == ModelBakery.MISSING) {
            return MISSING;
        }

        return fluidId == ModelBakery.NO_FLUID ? NO_FLUID : fluidId;
    }

    @Override
    public int metadata(int modelId) {
        BakedModel model = models.model(modelId);
        return model == null ? NO_METADATA : model.metadata();
    }
}
