package com.eminus.client.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.eminus.gpu.Location;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.settings.DetailDistance;
import com.eminus.settings.Settings;
import com.eminus.settings.ShaderPackLod;

import org.junit.jupiter.api.Test;

class FarRendererTest {
    private static final Settings BUILT = new Settings(true, 0, 16, 4, DetailDistance.MEDIUM, true, true, ShaderPackLod.DEFAULT);
    private static final Location MASK = new Location("eminus", "near_mask");
    private static final Location OPAQUE = new Location("eminus", "far_opaque");
    private static final Location TRANSLUCENT = new Location("eminus", "far_translucent");

    private record FakePipeline(Location location, boolean compiles) implements Pipeline {
    }

    @Test
    void theFirstProgramThatDoesNotCompileRefusesTheRenderer() {
        assertEquals(OPAQUE, FarRenderer.refusedProgram(List.of(new FakePipeline(MASK, true),
                new FakePipeline(OPAQUE, false), new FakePipeline(TRANSLUCENT, false))));
    }

    @Test
    void programsThatAllCompileRefuseNothing() {
        assertNull(FarRenderer.refusedProgram(List.of(new FakePipeline(MASK, true), new FakePipeline(OPAQUE, true),
                new FakePipeline(TRANSLUCENT, true))));
    }

    @Test
    void aFarDistanceChangeRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, new Settings(true, 0, 32, 4, DetailDistance.MEDIUM, true, true, ShaderPackLod.DEFAULT)));
    }

    @Test
    void aDetailDistanceChangeRecreatesTheRenderer() {
        assertTrue(FarRenderer.recreates(BUILT, new Settings(true, 0, 16, 4, DetailDistance.HIGH, true, true, ShaderPackLod.DEFAULT)));
    }

    @Test
    void aFogOrFadeChangeKeepsTheRenderer() {
        assertFalse(FarRenderer.recreates(BUILT, new Settings(true, 0, 16, 4, DetailDistance.MEDIUM, false, false, ShaderPackLod.DEFAULT)));
    }

    @Test
    void anIngestionOrWorkerChangeKeepsTheRenderer() {
        assertFalse(FarRenderer.recreates(BUILT, new Settings(false, 0, 16, 8, DetailDistance.MEDIUM, true, true, ShaderPackLod.DEFAULT)));
    }
}
