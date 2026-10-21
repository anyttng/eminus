package com.eminus.compat.sodium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SodiumCutoutTest {
    private static final int HALF = 2;
    private static final float NOT_READ = Float.NaN;
    private static final String SODIUM_0_8_TABLE = """
            const uint MATERIAL_ALPHA_CUTOFF_OFFSET = 1u;
            const float[4] ALPHA_CUTOFF = float[4](0.0, 0.1, 0.1, 1.0);
            """;
    private static final String SODIUM_0_9_TABLE = "const float[4] ALPHA_CUTOFF = float[4](0.0, 0.1, 0.5, 1.0);";

    @Test
    void theSodiumBuildTheSuiteCarriesHasALeafCutout() {
        assertFalse(Float.isNaN(SodiumCutout.read(NOT_READ)), "the Sodium jar of sodium_version has no ALPHA_CUTOFF"
                + " entry for HALF in include/chunk_material.glsl; the test log names what was read");
    }

    @Test
    void halfReadsTheTableEntryAtItsOrdinal() {
        assertEquals(0.1F, SodiumCutout.parse(SODIUM_0_8_TABLE, HALF));
        assertEquals(0.5F, SodiumCutout.parse(SODIUM_0_9_TABLE, HALF));
    }

    @Test
    void sourceWithoutTheTableReadsNothing() {
        assertNull(SodiumCutout.parse("const uint MATERIAL_USE_MIP_OFFSET = 0u;", HALF));
    }

    @Test
    void entryPastTheTableReadsNothing() {
        assertNull(SodiumCutout.parse("const float[2] ALPHA_CUTOFF = float[2](0.0, 0.1);", HALF));
    }
}
