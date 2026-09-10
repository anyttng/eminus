package com.eminus.mesh;

import com.eminus.cell.CellKey;
import com.eminus.cell.StateOpacity;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class CellMesher implements FacePasses.Sink, GreedyMerger.Emitter {
    private final MeshScratch scratch;
    private final MeshModels models;

    private Direction face;
    private int plane;

    public CellMesher(MeshScratch scratch, MeshModels models) {
        this.scratch = scratch;
        this.models = models;
    }

    public @Nullable CellMesh mesh(long key, int occupancy, StateOpacity opacity, Runnable whenBaked) {
        scratch.reset();
        FacePasses passes = new FacePasses(scratch, opacity, models, CellKey.level(key), whenBaked, this);

        if (!passes.run()) {
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

    private long placed(long data, int u, int v, int width, int height) {
        int ordinal = face.ordinal();

        return switch (face.getAxis()) {
            case X -> Quad.of(data, ordinal, plane, v, u, width, height);
            case Y -> Quad.of(data, ordinal, u, plane, v, width, height);
            case Z -> Quad.of(data, ordinal, u, v, plane, width, height);
        };
    }
}
