package com.eminus.api.v1;

/**
 * One reading of the far layer.
 *
 * @param dimension           the dimension the renderer draws, as its identifier
 * @param arena               the geometry arena at the call
 * @param tree                the node tree when its thread took the request
 * @param ingestQueued        chunk sections queued or being merged
 * @param pendingBlockChanges chunk sections whose block changes wait out their debounce before they are ingested
 */
public record FarLayerState(String dimension, ArenaState arena, TreeState tree, int ingestQueued,
        int pendingBlockChanges) {

    /**
     * Whether the far layer has nothing left to do for the camera it last saw: the tree is settled and no chunk
     * section is queued. Block changes still in their debounce do not count — they revise terrain already drawn,
     * and a live world never runs out of them.
     */
    public boolean settled() {
        return tree.settled() && ingestQueued == 0;
    }
}
