package com.eminus.render.arena.client;

import com.eminus.mesh.CellMesh;

public record ArenaUpload(CellMesh mesh, int block, int slot, long byteOffset) {
}
