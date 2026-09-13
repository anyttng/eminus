package com.eminus.render.tree;

import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.CellMesh;

public sealed interface TreeMessage {
    record CellChanged(CellHandle handle, int faceMask) implements TreeMessage {
    }

    record CellMeshed(CellMesh mesh, long request) implements TreeMessage {
    }

    record ColumnCovered(int chunkX, int chunkZ) implements TreeMessage {
    }

    record FrameReady() implements TreeMessage {
    }

    record Describe(long[] keys) implements TreeMessage {
    }
}
