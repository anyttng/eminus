package com.eminus.api.v1;

/**
 * Whether a far renderer runs on the client, and if not, why.
 */
public enum FarLayerStatus {
    /**
     * A far renderer draws the dimension the client holds.
     */
    RUNNING,
    /**
     * No far renderer runs because the client holds no level: before a world is joined, after it is left, and while
     * the client changes dimension.
     */
    NO_LEVEL,
    /**
     * The client holds a level, but the far renderer was refused: the render backend lacks a feature it needs, or
     * one of its programs did not compile. {@link EminusApi#refusal} says which. A resource reload, a change of
     * dimension, or a change of the far render distance or the detail distance tries again.
     */
    REFUSED
}
