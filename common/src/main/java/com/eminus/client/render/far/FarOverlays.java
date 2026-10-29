package com.eminus.client.render.far;

import java.util.ArrayList;
import java.util.List;

import com.eminus.box.BoxRegistry;
import com.eminus.client.frame.GameFrame;
import com.eminus.client.render.beam.BeamPass;
import com.eminus.client.render.box.BoxPass;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.backend.DepthConvention;
import com.eminus.session.DimensionRuntime;

import net.minecraft.client.multiplayer.ClientLevel;

import org.joml.Matrix4fc;

final class FarOverlays implements AutoCloseable {
    private final BoxPass boxes;
    private final BeamPass beams;

    private FarOverlays(BoxPass boxes, BeamPass beams) {
        this.boxes = boxes;
        this.beams = beams;
    }

    static FarOverlays create(Gpu gpu, DepthConvention depth) {
        return new FarOverlays(BoxPass.create(gpu, depth), BeamPass.create(gpu, depth));
    }

    List<Pipeline> pipelines() {
        List<Pipeline> pipelines = new ArrayList<>(boxes.pipelines());
        pipelines.add(beams.pipeline());
        return pipelines;
    }

    boolean drawsAny() {
        return boxes.drawsAny() || beams.drawsAny();
    }

    void update(DimensionRuntime runtime, ClientLevel level, GameFrame game) {
        boxes.update(BoxRegistry.get(), runtime.identity().dimension());
        beams.update(runtime.beams(), level, game);
    }

    void drawFar(FarTarget target, Matrix4fc farViewProjection, GameFrame game) {
        boxes.drawFar(target, farViewProjection, game);
        beams.draw(target, farViewProjection, game);
    }

    void drawNear(Texture color, Texture depth, Matrix4fc gameViewProjection, GameFrame game, float nearPlane) {
        boxes.drawNear(color, depth, gameViewProjection, game, nearPlane);
    }

    @Override
    public void close() {
        beams.close();
        boxes.close();
    }
}
