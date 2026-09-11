package com.eminus.handoff;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public final class CoverageSections {
    public static final int SECTION_INTS = 4;
    public static final int SECTION_BYTES = SECTION_INTS * Integer.BYTES;
    public static final int INITIAL_CAPACITY = 1024;

    private static final int UNUSED = 0;
    private static final int GROWTH = 2;

    private ByteBuffer bytes;
    private IntBuffer sections;
    private int count;

    public CoverageSections() {
        this(INITIAL_CAPACITY);
    }

    public CoverageSections(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("A coverage of " + capacity + " sections covers nothing.");
        }

        allocate(capacity);
    }

    public void clear() {
        count = 0;
        sections.clear();
    }

    public void add(int originX, int originY, int originZ) {
        if (count == capacity()) {
            grow();
        }

        sections.put(originX).put(originY).put(originZ).put(UNUSED);
        count++;
    }

    public int count() {
        return count;
    }

    public int capacity() {
        return bytes.capacity() / SECTION_BYTES;
    }

    public ByteBuffer buffer() {
        return bytes.clear().limit(count * SECTION_BYTES);
    }

    private void grow() {
        IntBuffer written = sections.flip();
        allocate(capacity() * GROWTH);
        sections.put(written);
    }

    private void allocate(int capacity) {
        bytes = ByteBuffer.allocateDirect(capacity * SECTION_BYTES).order(ByteOrder.nativeOrder());
        sections = bytes.asIntBuffer();
    }
}
