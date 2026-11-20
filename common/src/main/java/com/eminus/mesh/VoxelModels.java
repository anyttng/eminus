package com.eminus.mesh;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

import com.uber.nullaway.annotations.Initializer;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public final class VoxelModels {
    private static final int COORDINATE_BITS = 8;
    private static final int COORDINATE_MASK = (1 << COORDINATE_BITS) - 1;

    private final IntOpenHashSet missingStates = new IntOpenHashSet();
    private final IntOpenHashSet missingPositions = new IntOpenHashSet();

    private CellFrame frame;
    private long key;
    private boolean coarse;
    private int awaited;

    @Initializer
    public void begin(CellFrame frame, long key) {
        this.frame = frame;
        this.key = key;
        coarse = CellKey.level(key) > DetailLevel.MIN;
        missingStates.clear();
        missingPositions.clear();
        awaited = 0;
    }

    public int at(MeshModels models, int stateId, int x, int y, int z, Runnable whenBaked) {
        if (missingStates.contains(stateId)) {
            return MeshModels.MISSING;
        }

        int modelId = models.modelId(stateId, whenBaked);
        if (modelId == MeshModels.MISSING) {
            return missed(stateId);
        }

        if (modelId == MeshModels.POSITIONAL) {
            modelId = positional(models, stateId, x, y, z, whenBaked);
        }

        return coarse ? models.opaqueModelId(modelId) : modelId;
    }

    public int fluid(MeshModels models, int stateId, Runnable whenBaked) {
        if (missingStates.contains(stateId)) {
            return MeshModels.MISSING;
        }

        int fluidModel = models.fluidModelId(stateId, whenBaked);
        return fluidModel == MeshModels.MISSING ? missed(stateId) : fluidModel;
    }

    public int awaited() {
        return awaited;
    }

    private int positional(MeshModels models, int stateId, int x, int y, int z, Runnable whenBaked) {
        int position = ((x & COORDINATE_MASK) << (2 * COORDINATE_BITS))
                | ((y & COORDINATE_MASK) << COORDINATE_BITS) | (z & COORDINATE_MASK);
        if (missingPositions.contains(position)) {
            return MeshModels.MISSING;
        }

        int modelId = models.positionalModelId(stateId, CellFrame.blockXOf(key, x), frame.blockYOf(key, y),
                CellFrame.blockZOf(key, z), whenBaked);
        if (modelId == MeshModels.MISSING) {
            missingPositions.add(position);
            awaited++;
        }

        return modelId;
    }

    private int missed(int stateId) {
        missingStates.add(stateId);
        awaited++;
        return MeshModels.MISSING;
    }
}
