package com.eminus.api.v1;

/**
 * The tree's nodes on one detail level.
 *
 * @param level  the detail level, 0 the finest
 * @param nodes  the nodes held on it
 * @param meshed the nodes whose mesh has landed
 * @param quads  the quads those meshes carry
 */
public record LevelState(int level, int nodes, int meshed, long quads) {
}
