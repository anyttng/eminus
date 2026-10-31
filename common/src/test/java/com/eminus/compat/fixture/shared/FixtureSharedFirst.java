package com.eminus.compat.fixture.shared;

import com.eminus.compat.fixture.mod.FixtureModLater;

public final class FixtureSharedFirst {
    public static Object create() {
        return new FixtureModLater();
    }

    private FixtureSharedFirst() {
    }
}
