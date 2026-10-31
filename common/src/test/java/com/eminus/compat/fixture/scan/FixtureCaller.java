package com.eminus.compat.fixture.scan;

import com.eminus.compat.fixture.mod.FixtureModApi;
import com.eminus.compat.fixture.mod.FixtureModThing;

public final class FixtureCaller {
    public static Object call() {
        FixtureModApi api = new FixtureModApi();
        int read = api.count + api.answer() + api.hashCode();
        return read > 0 ? new FixtureModThing() : api;
    }

    private FixtureCaller() {
    }
}
