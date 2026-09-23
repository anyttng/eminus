package com.eminus.mesh;

import com.eminus.cell.CellFrame;
import com.eminus.cell.StateOpacity;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class CellMesher implements FacePasses.Sink, GreedyMerger.Emitter {
    private final MeshScratch scratch;
    private final MeshModels models;
    private final CellFrame frame;

    private Direction face;
    private int plane;

    public CellMesher(MeshScratch scratch, MeshModels models, CellFrame frame) {
        this.scratch = scratch;
        this.models = models;
        this.frame = frame;
    }

    public @Nullable CellMesh mesh(long key, int occupancy, StateOpacity opacity, Runnable whenBaked) {
        scratch.reset();
        scratch.offsets().begin(frame, key);
        scratch.voxelModels().begin(frame, key);
        FacePasses passes = new FacePasses(scratch, opacity, models, whenBaked, this);
        BladePass blades = new BladePass(scratch, models, whenBaked);

        if (!passes.run() || !blades.run()) {
            scratch.reset();
            return null;
        }

        return scratch.buffer().freeze(key, occupancy);
    }

    @Override
    public void accept(Direction towards, int at, FacePlane quads) {
        face = towards;
        plane = at;
        scratch.merger().merge(quads, this);
    }

    @Override
    public void emit(int u, int v, int width, int height, long data) {
        int group = QuadGroups.of(face, models.metadata(Quad.modelId(data)));
        scratch.buffer().add(group, placed(data, u, v, width, height));
    }

    @Override
    public boolean merges(long data) {
        return !ModelMetadata.has(models.metadata(Quad.modelId(data)), ModelMetadata.SLOPED);
    }

    private long placed(long data, int u, int v, int width, int height) {
        int ordinal = face.ordinal();

        return switch (face.getAxis()) {
            case X -> Quad.of(data, ordinal, plane, v, u, width, height);
            case Y -> Quad.of(data, ordinal, u, plane, v, width, height);
            case Z -> Quad.of(data, ordinal, u, v, plane, width, height);
        };
    }
}
