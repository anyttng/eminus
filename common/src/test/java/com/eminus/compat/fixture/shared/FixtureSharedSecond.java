package com.eminus.compat.fixture.shared;

import com.eminus.compat.fixture.mod.FixtureModApi;

public final class FixtureSharedSecond {
    public static int read(FixtureModApi api) {
        return api.answer();
    }

    private FixtureSharedSecond() {
    }
}
