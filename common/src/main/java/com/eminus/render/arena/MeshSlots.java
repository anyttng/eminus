package com.eminus.render.arena;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface MeshSlots {
    @Nullable MeshSlot slot(long key);
}
