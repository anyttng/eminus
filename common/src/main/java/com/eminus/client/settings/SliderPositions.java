package com.eminus.client.settings;

import com.eminus.settings.DetailDistance;
import com.eminus.settings.Settings;

public final class SliderPositions {
    public static final int FIRST = 0;
    public static final int LAST_LOWEST_STORED_LEVEL = Settings.MAX_DETAIL_LEVEL - Settings.MIN_DETAIL_LEVEL;
    public static final int LAST_DETAIL_DISTANCE = DetailDistance.values().length - 1;

    private static final DetailDistance[] DETAIL_DISTANCES = DetailDistance.values();

    public static int lowestStoredLevel(int position) {
        return Settings.MIN_DETAIL_LEVEL + Settings.MAX_DETAIL_LEVEL - position;
    }

    public static int lowestStoredLevelPosition(int level) {
        return Settings.MIN_DETAIL_LEVEL + Settings.MAX_DETAIL_LEVEL - level;
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
