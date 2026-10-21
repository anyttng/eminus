package com.eminus.compat.iris;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import com.eminus.client.gpu.opengl.TextureUnitBudget;
import com.eminus.client.render.far.PackSpecs;
import com.eminus.gpu.pipeline.PipelineSpec;

import org.junit.jupiter.api.Test;

class PackProgramUnitsTest {
    @Test
    void everyPackProgramFitsTheGameTrackedUnitsAfterTheOnesIrisReserves() {
        List<PipelineSpec> specs = PackSpecs.all();

        assertFalse(specs.isEmpty());
        for (PipelineSpec spec : specs) {
            assertDoesNotThrow(() -> TextureUnitBudget.require(spec, PackProgram.FIRST_UNIT));
        }
    }
}
