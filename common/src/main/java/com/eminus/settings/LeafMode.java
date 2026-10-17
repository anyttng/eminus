package com.eminus.settings;

public enum LeafMode {
    SEE_THROUGH,
    OPAQUE;

    public boolean cutout() {
        return this == SEE_THROUGH;
    }

    public static LeafMode of(boolean seeThrough) {
        return seeThrough ? SEE_THROUGH : OPAQUE;
    }
}
