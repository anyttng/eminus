package com.eminus.client.render.arena;

public record ArenaState(
        long bytes,
        long committedBytes,
        long pageBytes,
        int blocks,
        int meshes,
        int usedBlocks,
        int freeBlocks,
        int largestFreeRun,
        int freeRuns,
        long refusals,
        boolean pressure) {
}
