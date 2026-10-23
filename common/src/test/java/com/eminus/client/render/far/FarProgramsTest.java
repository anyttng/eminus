package com.eminus.client.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import com.eminus.gpu.Location;
import com.eminus.gpu.pipeline.Pipeline;

import org.junit.jupiter.api.Test;

class FarProgramsTest {
    private static final Location MASK = new Location("eminus", "near_mask");
    private static final Location OPAQUE = new Location("eminus", "far_opaque");
    private static final Location TRANSLUCENT = new Location("eminus", "far_translucent");

    private record FakePipeline(Location location, boolean compiles) implements Pipeline {
    }

    @Test
    void theFirstProgramThatDoesNotCompileRefusesTheRenderer() {
        assertEquals(OPAQUE, FarPrograms.refused(List.of(new FakePipeline(MASK, true),
                new FakePipeline(OPAQUE, false), new FakePipeline(TRANSLUCENT, false))));
    }

    @Test
    void programsThatAllCompileRefuseNothing() {
        assertNull(FarPrograms.refused(List.of(new FakePipeline(MASK, true), new FakePipeline(OPAQUE, true),
                new FakePipeline(TRANSLUCENT, true))));
    }
}
