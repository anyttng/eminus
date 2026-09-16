package com.eminus.client.render.arena;

import com.eminus.mesh.CellMesh;

public record ArenaUpload(CellMesh mesh, int block, long byteOffset) {
}
