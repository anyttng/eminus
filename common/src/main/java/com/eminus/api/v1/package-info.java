/**
 * The public surface of Eminus for other mods: whether the far layer runs on the client, how far it reaches, whether
 * it has settled, and the boxes another mod hands it to draw.
 *
 * <p>Everything under {@code com.eminus.api.v1} keeps its members within a major version. Everything outside this
 * package is internal: it moves without notice, and mixins into it are unsupported. {@link
 * com.eminus.api.v1.EminusApi} is the entry point: {@link com.eminus.api.v1.EminusApi#status} with a {@link
 * com.eminus.api.v1.FarLayerStatus}, {@link com.eminus.api.v1.EminusApi#farDistanceBlocks}, {@link
 * com.eminus.api.v1.EminusApi#farLayer} with a {@link com.eminus.api.v1.FarLayerState}, and {@link
 * com.eminus.api.v1.EminusApi#registerBoxes} with a {@link com.eminus.api.v1.FarBox} list and the {@link
 * com.eminus.api.v1.FarBoxGroup} it hands back.</p>
 */
package com.eminus.api.v1;
