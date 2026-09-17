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
 * @param pressure       whether the arena is holding: from crossing its high-water mark or refusing a mesh until it
 *                       falls below its low-water mark, the tree requests no children and evicts the nodes its last
 *                       walk did not use
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
