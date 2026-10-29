package com.eminus.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

class BeamTexelsTest {
    private static final int LAST_HEIGHT = 2048;
    private static final int RED = 0xFFB02E26;
    private static final int WHITE = 0xFFF9FFFE;

    @Test
    void sectionsStackFromTheBeaconAndTheLastOneReachesTheGivenHeight() {
        Beam beam = new Beam(BlockPos.asLong(-5, 60, 9), List.of(new BeamSection(RED, 3), new BeamSection(WHITE, 40)));
        ByteBuffer target = ByteBuffer.allocate(Math.toIntExact(BeamTexels.bytes(BeamTexels.sections(List.of(beam)))));

        BeamTexels.write(List.of(beam), LAST_HEIGHT, target);
        target.flip();

        assertEquals(2, BeamTexels.sections(List.of(beam)));
        assertSection(target, -5, 60, 9, RED, 63);
        assertSection(target, -5, 63, 9, WHITE, 63 + LAST_HEIGHT);
    }

    private static void assertSection(ByteBuffer read, int x, int bottom, int z, int argb, int top) {
        assertEquals(List.of(x, bottom, z, argb, top, 0, 0, 0),
                List.of(read.getInt(), read.getInt(), read.getInt(), read.getInt(), read.getInt(), read.getInt(),
                        read.getInt(), read.getInt()));
    }
}
