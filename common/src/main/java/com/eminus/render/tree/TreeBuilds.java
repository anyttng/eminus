package com.eminus.render.tree;

import com.eminus.cell.cache.CellHandle;

import org.jspecify.annotations.Nullable;

public interface TreeBuilds {
    // The handle arrives with as many references as the build has to release.
    void build(long key, @Nullable CellHandle handle, int references);

    void release(CellHandle handle, int references);

    int backlog();
}
