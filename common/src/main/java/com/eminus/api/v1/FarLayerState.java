package com.eminus.api.v1;

/**
 * One reading of the far layer.
 *
 * @param dimension the dimension the renderer draws, as its identifier
 * @param settled   whether the far layer has nothing left to do for the camera it last saw: nothing is queued or
 *                  being built, nothing more is asked for, and no chunk section waits to be stored. Block changes
 *                  still waiting do not count, since they revise terrain already drawn and a live world never runs
 *                  out of them.
 */
public record FarLayerState(String dimension, boolean settled) {
}
