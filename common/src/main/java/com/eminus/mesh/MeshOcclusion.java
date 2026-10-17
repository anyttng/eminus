package com.eminus.mesh;

import com.eminus.cell.DetailLevel;

@FunctionalInterface
public interface MeshOcclusion {
    MeshOcclusion EVERY_LEVEL = level -> true;
    MeshOcclusion FINEST_LEVEL = level -> level == DetailLevel.MIN;
    MeshOcclusion NONE = level -> false;

    boolean at(int level);
}
