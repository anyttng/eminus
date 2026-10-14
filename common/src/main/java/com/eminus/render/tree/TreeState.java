package com.eminus.render.tree;

import java.util.List;

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
        int waitingInView,
        FrontState front,
        List<LevelState> levels) {
}
