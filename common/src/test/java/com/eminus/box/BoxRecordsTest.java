package com.eminus.box;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import com.eminus.api.v1.FarBox;

import org.junit.jupiter.api.Test;

class BoxRecordsTest {
    private static final double WORLD_EDGE = 30_000_000.0;

    @Test
    void aCoordinateAtTheWorldEdgeSplitsExactly() {
        assertEquals(29_999_999, BoxRecords.block(WORLD_EDGE - 0.25));
        assertEquals(0.75F, BoxRecords.fraction(WORLD_EDGE - 0.25));
        assertEquals(-30_000_000, BoxRecords.block(-WORLD_EDGE + 0.75));
        assertEquals(0.75F, BoxRecords.fraction(-WORLD_EDGE + 0.75));
    }

    @Test
    void aNegativeCoordinateFloorsBelowZero() {
        assertEquals(-1, BoxRecords.block(-0.5));
        assertEquals(0.5F, BoxRecords.fraction(-0.5));
    }

    @Test
    void aRecordCarriesBothCornersTheColourAndTheFlag() {
        FarBox box = new FarBox(-WORLD_EDGE + 0.5, -64.0, 12.25, WORLD_EDGE - 0.5, 320.0, 13.0, 0x80FF8000, true);
        ByteBuffer written = ByteBuffer.allocate((int) BoxRecords.bytes(1)).order(ByteOrder.nativeOrder());

        BoxRecords.write(List.of(box), written);
        written.flip();

        assertEquals(BoxRecords.BYTES, written.remaining());
        assertEquals(List.of(-30_000_000, -64, 12, 0x80FF8000), ints(written, 4));
        assertEquals(List.of(29_999_999, 320, 13, BoxRecords.EMISSIVE), ints(written, 4));
        assertEquals(List.of(0.5F, 0.0F, 0.25F), floats(written));
        written.getInt();
        assertEquals(List.of(0.5F, 0.0F, 0.0F), floats(written));
    }

    private static List<Integer> ints(ByteBuffer buffer, int count) {
        Integer[] read = new Integer[count];
        for (int i = 0; i < count; i++) {
            read[i] = buffer.getInt();
        }
        return List.of(read);
    }

    private static List<Float> floats(ByteBuffer buffer) {
        return List.of(buffer.getFloat(), buffer.getFloat(), buffer.getFloat());
    }
}
