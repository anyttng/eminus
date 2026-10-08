package com.eminus.client.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.eminus.gpu.Std140;

import org.junit.jupiter.api.Test;

class FarFrameLayoutTest {
    // GLSL std140 for the FarFrame block in far_frame.glsl: a mat4, four ints, an ivec3 at 80 whose 4-byte tail
    // takes the first float.
    private static final int MAT4_BYTES = 64;
    private static final int INT_BYTES = 4;
    private static final int NEAR_ORIGIN_OFFSET = MAT4_BYTES + 4 * INT_BYTES;
    private static final int SHADE_DOWN_OFFSET = NEAR_ORIGIN_OFFSET + 3 * INT_BYTES;
    private static final List<String> SHADES = List.of("ShadeDown", "ShadeUp", "ShadeNorth", "ShadeSouth",
            "ShadeWest", "ShadeEast");

    @Test
    void theShadeFloatsStartInTheTailOfTheNearOrigin() {
        for (int shade = 0; shade < SHADES.size(); shade++) {
            assertEquals(SHADE_DOWN_OFFSET + shade * INT_BYTES, offset(SHADES.get(shade)));
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
