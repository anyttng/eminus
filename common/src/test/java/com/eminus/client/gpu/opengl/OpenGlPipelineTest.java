package com.eminus.client.gpu.opengl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.gpu.Format;
import com.eminus.gpu.Location;
import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.PipelineSpec;

import org.junit.jupiter.api.Test;

class OpenGlPipelineTest {
    private static final Location PIPELINE = new Location("eminus", "test");
    private static final String UNIFORM = "Frame";
    private static final String SAMPLER = "Sampler";
    private static final String TEXELS = "Texels";
    private static final int UNIT = 0;

    private static PipelineSpec spec(int textureBindings) {
        PipelineSpec.Builder builder = PipelineSpec.builder(PIPELINE, PIPELINE, PIPELINE)
                .withColourTarget(Format.RGBA8_UNORM, null, true)
                .withBinding(Binding.uniform(UNIFORM));
        for (int binding = 0; binding < textureBindings; binding++) {
            builder.withBinding(binding % 2 == 0 ? Binding.sampled(SAMPLER + binding)
                    : Binding.texel(TEXELS + binding, Format.R32_UINT));
        }
        return builder.build();
    }

    @Test
    void aPipelineWithinTheGameTrackedUnitsIsAccepted() {
        assertDoesNotThrow(() -> OpenGlPipeline.requireTextureUnits(spec(GameHandles.GAME_TRACKED_TEXTURE_UNITS)));
    }

    @Test
    void aTextureBindingPastTheGameTrackedUnitsIsRefusedWithThePipelineName() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> OpenGlPipeline.requireTextureUnits(spec(GameHandles.GAME_TRACKED_TEXTURE_UNITS + 1)));

        assertTrue(refused.getMessage().contains(PIPELINE.toString()), refused.getMessage());
    }

    @Test
    void aTexelViewOfTheDeclaredFormatBinds() {
        OpenGlPipeline.Slot slot = new OpenGlPipeline.Slot(Binding.Kind.TEXEL, UNIT, Format.RG32_UINT);

        assertDoesNotThrow(() -> slot.requireFormat(TEXELS, Format.RG32_UINT));
    }

    @Test
    void aTexelViewOfAnotherFormatIsRefusedAtBind() {
        OpenGlPipeline.Slot slot = new OpenGlPipeline.Slot(Binding.Kind.TEXEL, UNIT, Format.RG32_UINT);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> slot.requireFormat(TEXELS, Format.RGBA32_UINT));

        assertTrue(refused.getMessage().contains(TEXELS), refused.getMessage());
    }
}
