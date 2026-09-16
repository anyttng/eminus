/**
 * The public surface of Eminus for other mods: a read-only view of the far layer's state.
 *
 * <p>Everything under {@code com.eminus.api.v1} keeps its members within a major version. Everything outside this
 * package is internal — it moves without notice, and mixins into it are unsupported. {@link
 * com.eminus.api.v1.EminusApi#farLayer} is the entry point; it hands out a {@link com.eminus.api.v1.FarLayerState}
 * with the geometry arena in an {@link com.eminus.api.v1.ArenaState}, the node tree in a {@link
 * com.eminus.api.v1.TreeState} and each detail level in a {@link com.eminus.api.v1.LevelState}.</p>
 */
package com.eminus.api.v1;
