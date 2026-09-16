package com.eminus.api.v1;

/**
 * The geometry arena: one GPU buffer of quads, handed out in blocks of a fixed quad count.
 *
 * @param bytes          the buffer's size
 * @param blocks         the blocks it is divided into
 * @param meshes         the cell meshes it holds
 * @param usedBlocks     the blocks those meshes take
 * @param freeBlocks     the blocks left
 * @param largestFreeRun the longest run of adjacent free blocks — the largest mesh that still fits
 * @param freeRuns       the number of separate free runs
 * @param refusals       the meshes refused for want of a free run since the renderer started
 * @param pressure       whether the arena is past its high-water mark or refused a mesh in its last upload, which
 *                       makes the tree evict the nodes it has seen least recently
 */
public record ArenaState(
        long bytes,
        int blocks,
        int meshes,
        int usedBlocks,
        int freeBlocks,
        int largestFreeRun,
        int freeRuns,
        long refusals,
        boolean pressure) {
}
