package com.eminus.mesh;

import java.util.function.IntPredicate;

import com.eminus.cell.DetailLevel;

public record MeshOcclusion(IntPredicate levels, CornerRule rule) {
    public static final MeshOcclusion EVERY_LEVEL = new MeshOcclusion(level -> true, CornerRule.VANILLA);
    public static final MeshOcclusion FINEST_LEVEL = new MeshOcclusion(level -> level == DetailLevel.MIN,
            CornerRule.VANILLA);
    public static final MeshOcclusion NONE = new MeshOcclusion(level -> false, CornerRule.VANILLA);

    public boolean at(int level) {
        return levels.test(level);
    }

    public CornerRule corners(int level) {
        return at(level) ? rule : CornerRule.NONE;
    }

    public MeshOcclusion withRule(CornerRule corners) {
        return new MeshOcclusion(levels, corners);
    }
}
