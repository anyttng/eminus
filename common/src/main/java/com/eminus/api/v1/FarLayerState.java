package com.eminus.api.v1;

/**
 * One reading of the far layer.
 *
 * @param dimension the dimension the renderer draws, as its identifier
 * @param settled   whether the far layer has nothing left to do for the camera it last saw: no mesh build is queued
 *                  or running, the last walk over the view asked for nothing more, and no chunk section waits to be
 *                  merged. Block changes still in their debounce do not count: they revise terrain already drawn,
 *                  and a live world never runs out of them.
 */
public record FarLayerState(String dimension, boolean settled) {
}
