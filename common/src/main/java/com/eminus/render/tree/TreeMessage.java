package com.eminus.render.tree;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.eminus.api.v1.TreeState;
import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.CellMesh;

public sealed interface TreeMessage {
    record CellChanged(CellHandle handle, int faceMask, int edgeMask) implements TreeMessage {
    }

    record CellMeshed(CellMesh mesh, long request) implements TreeMessage {
    }

    record ColumnCovered(int chunkX, int chunkZ) implements TreeMessage {
    }

    record FrameReady() implements TreeMessage {
    }

    record Describe(List<long[]> rows, CompletableFuture<List<long[]>> answer) implements TreeMessage {
    }

    record Snapshot(CompletableFuture<TreeState> answer) implements TreeMessage {
    }
}
