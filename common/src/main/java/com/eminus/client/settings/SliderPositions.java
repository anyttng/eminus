package com.eminus.client.settings;

import com.eminus.settings.DetailDistance;
import com.eminus.settings.LodChoice;

public final class SliderPositions {
    public static final int FIRST = 0;
    public static final int LAST_DETAIL_DISTANCE = DetailDistance.values().length - 1;

    private static final LodChoice[] LOD_CHOICES = {
            LodChoice.MINIMAL, LodChoice.LOW, LodChoice.MEDIUM, LodChoice.HIGH, LodChoice.ULTRA, LodChoice.CUSTOM};
    private static final DetailDistance[] DETAIL_DISTANCES = DetailDistance.values();

    public static final int LAST_LOD_CHOICE = LOD_CHOICES.length - 1;

    public static LodChoice lodChoice(int position) {
        return LOD_CHOICES[position];
    }

    public static int lodChoicePosition(LodChoice choice) {
        for (int position = FIRST; position <= LAST_LOD_CHOICE; position++) {
            if (LOD_CHOICES[position] == choice) {
                return position;
            }
        }

        throw new IllegalArgumentException("LOD choice " + choice + " has no slider position");
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
