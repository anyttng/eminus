package com.eminus.settings;

public enum LeafMode {
    SEE_THROUGH,
    OPAQUE;

    public boolean seeThrough() {
        return this == SEE_THROUGH;
    }

    public static LeafMode of(boolean seeThrough) {
        return seeThrough ? SEE_THROUGH : OPAQUE;
    }
}
