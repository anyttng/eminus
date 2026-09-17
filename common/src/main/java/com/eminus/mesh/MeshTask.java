package com.eminus.mesh;

import com.eminus.cell.CellKey;
import com.eminus.cell.cache.CellHandle;

import org.jspecify.annotations.Nullable;

public record MeshTask(long key, boolean retried, @Nullable CellHandle held, int references, long request,
        float priority) {
    public static final long NO_REQUEST = 0L;
    public static final float LOWEST_PRIORITY = 0.0F;

    private static final int NO_REFERENCES = 0;

    public static MeshTask fresh(long key) {
        return new MeshTask(key, false, null, NO_REFERENCES, NO_REQUEST, LOWEST_PRIORITY);
    }

    public static MeshTask carrying(long key, @Nullable CellHandle held, int references, long request,
            float priority) {
        return new MeshTask(key, false, held, references, request, priority);
    }

    public int level() {
        return CellKey.level(key);
    }

    public MeshTask retry() {
        return new MeshTask(key, true, null, NO_REFERENCES, request, priority);
    }
}
