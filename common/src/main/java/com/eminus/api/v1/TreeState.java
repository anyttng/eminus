package com.eminus.api.v1;

import java.util.List;

/**
 * The node tree the far layer is drawn from.
 *
 * @param walks             the traversals run since the renderer started
 * @param nodes             the nodes held, on every level
 * @param freeNodes         the nodes the table can still add
 * @param drawnMeshes       the meshes the last traversal listed for drawing
 * @param buildBacklog      the mesh builds queued and not yet taken by a worker
 * @param buildingNodes     the nodes whose mesh is being built
 * @param lastRequested     the children the last traversal requested
 * @param starved           whether the last traversal left children unrequested for want of budget or node room
 * @param walkPending       whether a change since the last traversal still waits for the next one
 * @param batchWaiting      whether meshes or evictions wait to reach the render thread
 * @param pressureEvictions the nodes evicted by traversals run while the arena was under pressure, since the renderer
 *                          started — the arena holding less than the view asks for, before any mesh is refused
 * @param settled           whether no build is queued or running, the last traversal requested nothing and was not
 *                          starved, no walk is pending and no batch is waiting
 * @param levels            one entry per detail level, finest first
 */
public record TreeState(
        long walks,
        int nodes,
        int freeNodes,
        int drawnMeshes,
        int buildBacklog,
        int buildingNodes,
        int lastRequested,
        boolean starved,
        boolean walkPending,
        boolean batchWaiting,
        long pressureEvictions,
        boolean settled,
        List<LevelState> levels) {
}
