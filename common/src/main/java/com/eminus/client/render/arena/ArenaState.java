package com.eminus.client.render.arena;

public record ArenaState(
        long bytes,
        long committedBytes,
        long pageBytes,
        int segments,
        int meshes,
        int usedSegments,
        int freeSegments,
        int largestFreeRun,
        int freeRuns,
        long refusals,
        boolean pressure) {
}
