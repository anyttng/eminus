package com.eminus.compat.fixture.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.eminus.compat.fixture.mod.FixtureModApi;
import com.eminus.compat.fixture.mod.FixtureModLater;

@Mixin(FixtureModApi.class)
public abstract class FixtureSharedFirstMixin {
    public static Object later() {
        return new FixtureModLater();
    }
}
