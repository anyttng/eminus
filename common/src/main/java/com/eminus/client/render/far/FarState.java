package com.eminus.client.render.far;

import com.eminus.client.render.arena.ArenaState;
import com.eminus.render.tree.TreeState;

public record FarState(String dimension, ArenaState arena, TreeState tree, int ingestQueued,
        int pendingBlockChanges) {

    public boolean settled() {
        return tree.settled() && ingestQueued == 0;
    }
}
