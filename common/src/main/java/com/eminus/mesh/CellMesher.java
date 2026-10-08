package com.eminus.mesh;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class CellMesher implements FacePasses.Sink, GreedyMerger.Emitter {
    private final MeshScratch scratch;
    private final MeshModels models;

    private Direction face;
    private int plane;
    private boolean border;
    private int level;
    private boolean coarse;

    public CellMesher(MeshScratch scratch, MeshModels models) {
        this.scratch = scratch;
        this.models = models;
    }

    public @Nullable CellMesh mesh(long key, int occupancy, StateOpacity opacity, Runnable whenBaked) {
        level = CellKey.level(key);
        coarse = level > DetailLevel.MIN;
        FacePasses passes = new FacePasses(scratch, opacity, models, level, whenBaked, this);
        BladePass blades = new BladePass(scratch, models, whenBaked);

        if (!passes.run() || !blades.run()) {
            scratch.reset();
            return null;
        }

        if (scratch.buffer().lostPlacements()) {
            scratch.buffer().resetReserving();
            passes.run();
            blades.run();
        }

        return scratch.buffer().freeze(key, occupancy);
    }

    @Override
    public void accept(Direction towards, int at, FacePlane quads, boolean onBorder) {
        face = towards;
        plane = at;
        border = onBorder;
        scratch.merger().merge(quads, this);
    }

    @Override
    public void emit(int u, int v, int width, int height, long data) {
        int group = border ? QuadGroups.border(face) : QuadGroups.of(face, models.metadata(Quad.modelId(data)));
        scratch.buffer().add(group, placed(data, u, v, width, height));
    }

    @Override
    public boolean merges(long data) {
        return !ModelMetadata.has(models.metadata(Quad.modelId(data)), ModelMetadata.SLOPED);
    }

    @Override
    public boolean stacks(long data) {
        int placement = scratch.buffer().placementAt(Quad.colourIndex(data));
        return !coarse || face.getAxis() == Direction.Axis.Y
                || QuadPlacement.gaps(level, placement) == VoxelEntry.NO_GAPS && models.fillsHeight(Quad.modelId(data));
    }

    private long placed(long data, int u, int v, int width, int height) {
        Direction.Axis normal = face.getAxis();
        return Quad.of(data, face.ordinal(), PlaneAxes.x(normal, u, v, plane), PlaneAxes.y(normal, u, v, plane),
                PlaneAxes.z(normal, u, v, plane), width, height);
    }
}
