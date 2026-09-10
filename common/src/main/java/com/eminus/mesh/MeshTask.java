package com.eminus.mesh;

import com.eminus.cell.CellKey;

public record MeshTask(long key, boolean retried) {
    public static MeshTask fresh(long key) {
        return new MeshTask(key, false);
    }

    public int level() {
        return CellKey.level(key);
    }

    public MeshTask retry() {
        return new MeshTask(key, true);
    }
}
