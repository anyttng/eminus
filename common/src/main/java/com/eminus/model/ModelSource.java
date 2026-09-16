package com.eminus.model;

public interface ModelSource {
    int modelCount();

    BakedModel model(int modelId);
}
