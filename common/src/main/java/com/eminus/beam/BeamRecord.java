package com.eminus.beam;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class BeamRecord {
    public static final int VERSION = 1;
    public static final int MAX_SECTIONS = 4096;

    private static final int HEADER_BYTES = 2 * Integer.BYTES;
    private static final int SECTION_BYTES = 2 * Integer.BYTES;

    public static byte[] encode(List<BeamSection> sections) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + sections.size() * SECTION_BYTES)
                .putInt(VERSION).putInt(sections.size());
        for (BeamSection section : sections) {
            buffer.putInt(section.argb()).putInt(section.height());
        }

        return buffer.array();
    }

    public static List<BeamSection> decode(byte[] record) {
        if (record.length < HEADER_BYTES) {
            throw new IllegalArgumentException("A beam record of " + record.length + " bytes holds no header.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(record);
        int version = buffer.getInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("Beam record version " + version + " is not " + VERSION + ".");
        }

        int count = buffer.getInt();
        if (count <= 0 || count > MAX_SECTIONS || record.length != HEADER_BYTES + count * SECTION_BYTES) {
            throw new IllegalArgumentException("A beam record of " + record.length + " bytes cannot hold " + count
                    + " sections.");
        }

        List<BeamSection> sections = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int argb = buffer.getInt();
            int height = buffer.getInt();
            if (height <= 0) {
                throw new IllegalArgumentException("Beam section " + index + " has height " + height + ".");
            }
            sections.add(new BeamSection(argb, height));
        }

        return List.copyOf(sections);
    }

    private BeamRecord() {
    }
}
