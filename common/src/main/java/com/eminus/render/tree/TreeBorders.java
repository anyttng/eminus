package com.eminus.render.tree;

import java.util.List;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.mesh.MeshSummary;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.Direction;

final class TreeBorders {
    private static final Direction[] FACES = Direction.values();

    private final LongSet drawnKeys;
    private final LongSet outOfViewDrawnKeys;
    private final LongSet descendedKeys;
    private final LongSet outOfViewDescendedKeys;

    TreeBorders(LongSet drawnKeys, LongSet outOfViewDrawnKeys, LongSet descendedKeys,
            LongSet outOfViewDescendedKeys) {
        this.drawnKeys = drawnKeys;
        this.outOfViewDrawnKeys = outOfViewDrawnKeys;
        this.descendedKeys = descendedKeys;
        this.outOfViewDescendedKeys = outOfViewDescendedKeys;
    }

    Long2IntMap of(List<MeshSummary> meshes) {
        Long2IntOpenHashMap borders = new Long2IntOpenHashMap();
        for (MeshSummary mesh : meshes) {
            int faces = borderFaces(mesh.key());
            if (faces != RenderList.NO_BORDER_FACES) {
                borders.put(mesh.key(), faces);
            }
        }

        return borders;
    }

    private int borderFaces(long key) {
        int faces = RenderList.NO_BORDER_FACES;
        for (Direction face : FACES) {
            long neighbor = CellKey.neighbor(key, face);
            if (!drawn(neighbor) && (descended(neighbor) || ancestorDrawn(neighbor))) {
                faces |= 1 << face.get3DDataValue();
            }
        }

        return faces;
    }

    private boolean ancestorDrawn(long key) {
        long ancestor = key;
        for (int level = CellKey.level(key) + 1; level <= DetailLevel.MAX; level++) {
            ancestor = CellKey.parent(ancestor);
            if (drawn(ancestor)) {
                return true;
            }
        }

        return false;
    }

    private boolean drawn(long key) {
        return drawnKeys.contains(key) || outOfViewDrawnKeys.contains(key);
    }

    private boolean descended(long key) {
        return descendedKeys.contains(key) || outOfViewDescendedKeys.contains(key);
    }
}
