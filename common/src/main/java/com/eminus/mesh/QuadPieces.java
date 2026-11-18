package com.eminus.mesh;

public final class QuadPieces {
    public static final int QUADS_PER_ENTRY = 8;
    public static final int MAX_ENTRIES = Math.ceilDiv(MeshBuffer.MAX_QUADS, QUADS_PER_ENTRY);
    public static final int SEARCH_STEPS = Integer.SIZE - Integer.numberOfLeadingZeros(MAX_ENTRIES - 1);

    private QuadPieces() {
    }

    public static int of(long quad) {
        return Quad.width(quad) * Quad.height(quad);
    }

    public static int[] before(long[] quads, int count) {
        int[] before = new int[count + 1];
        for (int quad = 0; quad < count; quad++) {
            before[quad + 1] = before[quad] + of(quads[quad]);
        }
        return before;
    }

    public static int[] entries(int[] before, int quads) {
        int[] entries = new int[Math.ceilDiv(quads, QUADS_PER_ENTRY)];
        for (int entry = 0; entry < entries.length; entry++) {
            entries[entry] = before[entry * QUADS_PER_ENTRY];
        }
        return entries;
    }
}
