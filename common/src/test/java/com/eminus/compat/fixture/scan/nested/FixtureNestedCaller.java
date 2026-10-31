package com.eminus.compat.fixture.scan.nested;

import com.eminus.compat.fixture.mod.FixtureModLater;

public final class FixtureNestedCaller {
    public static Object create() {
        return new FixtureModLater();
    }

    private FixtureNestedCaller() {
    }
}
