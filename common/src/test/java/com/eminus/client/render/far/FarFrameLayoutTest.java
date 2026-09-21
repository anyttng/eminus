package com.eminus.client.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.eminus.handoff.NearSections;

import com.mojang.blaze3d.buffers.Std140Builder;

import net.minecraft.world.level.CardinalLighting;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class FarFrameLayoutTest {
    // GLSL std140 for the FarFrame block in far_quads.vsh: a mat4, four ints, an ivec3 at 80 whose 4-byte tail
    // takes the first float.
    private static final int MAT4_BYTES = 64;
    private static final int INT_BYTES = 4;
    private static final int NEAR_ORIGIN_OFFSET = MAT4_BYTES + 4 * INT_BYTES;
    private static final int SHADE_DOWN_OFFSET = NEAR_ORIGIN_OFFSET + 3 * INT_BYTES;
    private static final float DELTA = 0.0F;

    private static final CardinalLighting SHADE = new CardinalLighting(0.1F, 0.2F, 0.3F, 0.4F, 0.5F, 0.6F);

    @Test
    void theShadeFloatsStartInTheTailOfTheNearOrigin() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(FarFrame.SIZE).order(ByteOrder.nativeOrder());

        FarFrame.layout(Std140Builder.intoBuffer(bytes), new Matrix4f(), 0, 0, new NearSections(), SHADE);

        assertEquals(SHADE.down(), bytes.getFloat(SHADE_DOWN_OFFSET), DELTA);
        assertEquals(SHADE.up(), bytes.getFloat(SHADE_DOWN_OFFSET + INT_BYTES), DELTA);
        assertEquals(SHADE.north(), bytes.getFloat(SHADE_DOWN_OFFSET + 2 * INT_BYTES), DELTA);
        assertEquals(SHADE.south(), bytes.getFloat(SHADE_DOWN_OFFSET + 3 * INT_BYTES), DELTA);
        assertEquals(SHADE.west(), bytes.getFloat(SHADE_DOWN_OFFSET + 4 * INT_BYTES), DELTA);
        assertEquals(SHADE.east(), bytes.getFloat(SHADE_DOWN_OFFSET + 5 * INT_BYTES), DELTA);
    }
}
