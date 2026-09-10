package com.eminus.mesh;

import com.eminus.cell.CellKey;
import com.eminus.cell.cache.CellHandle;

import org.jspecify.annotations.Nullable;

public record MeshTask(long key, boolean retried, @Nullable CellHandle held, int references) {
    private static final int NO_REFERENCES = 0;

    public static MeshTask fresh(long key) {
        return new MeshTask(key, false, null, NO_REFERENCES);
    }

    public static MeshTask carrying(long key, @Nullable CellHandle held, int references) {
        return new MeshTask(key, false, held, references);
    }

    public int level() {
        return CellKey.level(key);
    }

    public MeshTask retry() {
        return new MeshTask(key, true, null, NO_REFERENCES);
    }
}
