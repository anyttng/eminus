package com.eminus.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class BeamRecordTest {
    private static final List<BeamSection> SECTIONS = List.of(new BeamSection(0xFFB02E26, 2),
            new BeamSection(0xFF3C44AA, 1), new BeamSection(0xFFF9FFFE, 180));

    @Test
    void sectionsComeBackInOrder() {
        assertEquals(SECTIONS, BeamRecord.decode(BeamRecord.encode(SECTIONS)));
    }

    @Test
    void aTruncatedRecordIsRefused() {
        byte[] record = BeamRecord.encode(SECTIONS);

        assertThrows(IllegalArgumentException.class,
                () -> BeamRecord.decode(Arrays.copyOf(record, record.length - 1)));
    }

    @Test
    void anotherVersionIsRefused() {
        byte[] record = BeamRecord.encode(SECTIONS);
        ByteBuffer.wrap(record).putInt(BeamRecord.VERSION + 1);

        assertThrows(IllegalArgumentException.class, () -> BeamRecord.decode(record));
    }

    @Test
    void aRecordWithoutSectionsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> BeamRecord.decode(BeamRecord.encode(List.of())));
    }
}
