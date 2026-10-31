package com.eminus.compat.fixture.scan;

import com.eminus.compat.fixture.mod.FixtureModLater;

public final class FixtureOptionalHolder {
    public static Object create() {
        return new FixtureModLater();
    }

    private FixtureOptionalHolder() {
    }
}
