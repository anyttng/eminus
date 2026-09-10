package com.eminus.ingest;

import com.eminus.cell.Cell;
import com.eminus.cell.CellFrame;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.cache.CellAccess;
import com.eminus.cell.cache.CellHandle;

public final class CellMerger {
    private final CellAccess cells;
    private final CellFrame frame;
    private final int lowestStoredLevel;
    private final CellChangeListener listener;

    public CellMerger(CellAccess cells, CellFrame frame, int lowestStoredLevel, CellChangeListener listener) {
        this.cells = cells;
        this.frame = frame;
        this.lowestStoredLevel = lowestStoredLevel;
        this.listener = listener;
    }

    public void merge(SectionPyramid pyramid, int sectionX, int sectionY, int sectionZ) {
        int blockX = sectionX * SectionPyramid.SECTION_SIDE;
        int blockY = sectionY * SectionPyramid.SECTION_SIDE;
        int blockZ = sectionZ * SectionPyramid.SECTION_SIDE;

        for (int level = lowestStoredLevel; level <= DetailLevel.MAX; level++) {
            if (!mergeLevel(pyramid, level, blockX, blockY, blockZ)) {
                return;
            }
        }
    }

    private boolean mergeLevel(SectionPyramid pyramid, int level, int blockX, int blockY, int blockZ) {
        CellHandle handle = cells.open(frame.keyAt(level, blockX, blockY, blockZ));
        Cell cell = handle.cell();
        long[] source = pyramid.level(level);
        int side = SectionPyramid.sideOf(level);
        int originX = frame.voxelX(blockX, level);
        int originY = frame.voxelY(blockY, level);
        int originZ = frame.voxelZ(blockZ, level);
        int faceMask = FaceMask.NONE;
        boolean changed = false;

        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    int voxelX = originX + x;
                    int voxelY = originY + y;
                    int voxelZ = originZ + z;

                    if (cell.set(voxelX, voxelY, voxelZ, source[SectionPyramid.indexAt(side, x, y, z)])) {
                        changed = true;
                        faceMask |= FaceMask.of(voxelX, voxelY, voxelZ);
                    }
                }
            }
        }

        if (!changed) {
            cells.release(handle);
            return false;
        }

        handle.markDirty();
        listener.changed(handle, faceMask);
        return true;
    }
}
