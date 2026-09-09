package com.eminus.settings;

public final class FarDistance {
    public static final int BLOCKS_PER_TOP_LEVEL_CELL = 512;
    public static final int BLOCKS_PER_CHUNK = 16;
    public static final int CHUNKS_PER_TOP_LEVEL_CELL = BLOCKS_PER_TOP_LEVEL_CELL / BLOCKS_PER_CHUNK;

    public static int cellsToChunks(int cells) {
        return cells * CHUNKS_PER_TOP_LEVEL_CELL;
    }

    public static int chunksToCells(int chunks) {
        return Math.ceilDiv(chunks, CHUNKS_PER_TOP_LEVEL_CELL);
    }

    private FarDistance() {
    }
}
