/**
 * The public surface of Eminus for other mods: whether the far layer runs on the client, how far it reaches, and
 * whether it has settled.
 *
 * <p>Everything under {@code com.eminus.api.v1} keeps its members within a major version. Everything outside this
 * package is internal: it moves without notice, and mixins into it are unsupported. {@link
 * com.eminus.api.v1.EminusApi} is the entry point: {@link com.eminus.api.v1.EminusApi#status} with a {@link
 * com.eminus.api.v1.FarLayerStatus}, {@link com.eminus.api.v1.EminusApi#farDistanceBlocks}, and {@link
 * com.eminus.api.v1.EminusApi#farLayer} with a {@link com.eminus.api.v1.FarLayerState}.</p>
 */
package com.eminus.api.v1;
