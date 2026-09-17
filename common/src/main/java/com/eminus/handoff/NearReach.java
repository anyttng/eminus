package com.eminus.handoff;

import com.eminus.settings.FarDistance;

public final class NearReach {
    public static float blocks(int keptChunks, double eyeY, int minBlockY, int endBlockY) {
        double horizontal = (keptChunks + 1) * (double) FarDistance.BLOCKS_PER_CHUNK;
        double vertical = Math.max(eyeY - minBlockY, endBlockY - eyeY);
        return (float) Math.sqrt(2.0 * horizontal * horizontal + vertical * vertical);
    }

    private NearReach() {
    }
}
