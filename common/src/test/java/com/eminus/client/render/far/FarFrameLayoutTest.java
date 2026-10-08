package com.eminus.client.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.eminus.gpu.Std140;

import org.junit.jupiter.api.Test;

class FarFrameLayoutTest {
    // GLSL std140 for the FarFrame block in far_frame.glsl: the 4-byte tail of the NearOrigin ivec3 takes the first
    // float.
    private static final int INT_BYTES = 4;
    private static final int IVEC3_BYTES = 3 * INT_BYTES;
    private static final String NEAR_ORIGIN = "NearOrigin";
    private static final List<String> SHADES = List.of("ShadeDown", "ShadeUp", "ShadeNorth", "ShadeSouth",
            "ShadeWest", "ShadeEast");

    @Test
    void theShadeFloatsStartInTheTailOfTheNearOrigin() {
        int shadeDown = offset(NEAR_ORIGIN) + IVEC3_BYTES;
        for (int shade = 0; shade < SHADES.size(); shade++) {
            assertEquals(shadeDown + shade * INT_BYTES, offset(SHADES.get(shade)));
        }
    }

    private static int offset(String name) {
        return FarFrame.BLOCK.members().stream()
                .filter(member -> member.name().equals(name))
                .findFirst()
                .map(Std140.Member::offset)
                .orElseThrow();
    }
}
