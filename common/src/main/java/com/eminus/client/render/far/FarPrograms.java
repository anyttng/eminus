package com.eminus.client.render.far;

import java.util.ArrayList;
import java.util.List;

import com.eminus.client.handoff.NearMaskPass;
import com.eminus.gpu.Location;
import com.eminus.gpu.pipeline.Pipeline;

import org.jspecify.annotations.Nullable;

final class FarPrograms {
    private static final String PROGRAM_REFUSED = "program %s did not compile";

    private FarPrograms() {
    }

    static @Nullable String refusal(NearMaskPass mask, OpaquePass opaque, TranslucentPass translucent,
            CompositePass composite, FarOverlays overlays) {
        List<Pipeline> pipelines = new ArrayList<>(List.of(mask.pipeline()));
        pipelines.addAll(opaque.pipelines());
        pipelines.addAll(translucent.pipelines());
        pipelines.add(composite.pipeline());
        pipelines.addAll(overlays.pipelines());
        Location refused = refused(pipelines);
        return refused == null ? null : PROGRAM_REFUSED.formatted(refused);
    }

    static @Nullable Location refused(List<Pipeline> pipelines) {
        for (Pipeline pipeline : pipelines) {
            if (!pipeline.compiles()) {
                return pipeline.location();
            }
        }

        return null;
    }
}
