package com.eminus.ingest;

import com.eminus.cell.cache.CellHandle;

@FunctionalInterface
public interface CellChangeListener {
    // The handle arrives with a reference the listener releases.
    void changed(CellHandle handle, int faceMask, int edgeMask, int occupancy, long sequence);

    default void covered(int chunkX, int chunkZ) {
    }
}
