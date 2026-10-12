package com.eminus.client.settings;

import com.eminus.settings.DetailDistance;
import com.eminus.settings.LodQuality;

public final class SliderPositions {
    public static final int FIRST = 0;
    public static final int LAST_LOD_QUALITY = LodQuality.values().length - 1;
    public static final int LAST_DETAIL_DISTANCE = DetailDistance.values().length - 1;

    private static final LodQuality[] LOD_QUALITIES = LodQuality.values();
    private static final DetailDistance[] DETAIL_DISTANCES = DetailDistance.values();

    public static LodQuality lodQuality(int position) {
        return LOD_QUALITIES[LAST_LOD_QUALITY - position];
    }

    @SuppressWarnings("EnumOrdinal")
    public static int lodQualityPosition(LodQuality quality) {
        return LAST_LOD_QUALITY - quality.ordinal();
    }

    public static DetailDistance detailDistance(int position) {
        return DETAIL_DISTANCES[LAST_DETAIL_DISTANCE - position];
    }

    @SuppressWarnings("EnumOrdinal")
    public static int detailDistancePosition(DetailDistance distance) {
        return LAST_DETAIL_DISTANCE - distance.ordinal();
    }

    private SliderPositions() {
    }
}
