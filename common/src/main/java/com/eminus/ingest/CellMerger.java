package com.eminus.ingest;

import com.eminus.cell.Cell;
import com.eminus.cell.CellFrame;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.EdgeMask;
import com.eminus.cell.FaceMask;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellAccess;
import com.eminus.cell.cache.CellHandle;

public final class CellMerger {
    private static final long UNCHANGED = -1L;

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

    public boolean storesBeyondOpenSky(int sectionX, int sectionY, int sectionZ) {
        int blockX = sectionX * SectionPyramid.SECTION_SIDE;
        int blockY = sectionY * SectionPyramid.SECTION_SIDE;
        int blockZ = sectionZ * SectionPyramid.SECTION_SIDE;
        int side = SectionPyramid.sideOf(lowestStoredLevel);
        int originX = frame.voxelX(blockX, lowestStoredLevel);
        int originY = frame.voxelY(blockY, lowestStoredLevel);
        int originZ = frame.voxelZ(blockZ, lowestStoredLevel);
        CellHandle handle = cells.open(frame.keyAt(lowestStoredLevel, blockX, blockY, blockZ));

        try {
            return handle.withCell(cell -> holdsBeyondOpenSky(cell, side, originX, originY, originZ));
        } finally {
            cells.release(handle);
        }
    }

    private static boolean holdsBeyondOpenSky(Cell cell, int side, int originX, int originY, int originZ) {
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    if (!VoxelEntry.isOpenSky(cell.get(originX + x, originY + y, originZ + z))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean mergeLevel(SectionPyramid pyramid, int level, int blockX, int blockY, int blockZ) {
        CellHandle handle = cells.open(frame.keyAt(level, blockX, blockY, blockZ));
        long[] source = pyramid.level(level);
        int side = SectionPyramid.sideOf(level);
        int originX = frame.voxelX(blockX, level);
        int originY = frame.voxelY(blockY, level);
        int originZ = frame.voxelZ(blockZ, level);

        long reach = handle.withCell(cell -> writeLevel(cell, source, side, originX, originY, originZ));

        if (reach == UNCHANGED) {
            cells.release(handle);
            return false;
        }

        handle.markDirty();
        listener.changed(handle, (int) reach, (int) (reach >>> Integer.SIZE));
        return true;
    }

    private static long writeLevel(Cell cell, long[] source, int side, int originX, int originY, int originZ) {
        int faceMask = FaceMask.NONE;
        int edgeMask = EdgeMask.NONE;
        boolean changed = false;

        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    int voxelX = originX + x;
                    int voxelY = originY + y;
                    int voxelZ = originZ + z;
                    long entry = source[SectionPyramid.indexAt(side, x, y, z)];
                    if (VoxelEntry.biome(entry) == VoxelEntry.KEPT_BIOME) {
                        entry = VoxelEntry.withBiome(entry, VoxelEntry.biome(cell.get(voxelX, voxelY, voxelZ)));
                    }

                    if (cell.set(voxelX, voxelY, voxelZ, entry)) {
                        changed = true;
                        int voxelFaces = FaceMask.of(voxelX, voxelY, voxelZ);
                        faceMask |= voxelFaces;
                        edgeMask |= EdgeMask.of(voxelFaces);
                    }
                }
            }
        }

        return changed ? (long) edgeMask << Integer.SIZE | faceMask : UNCHANGED;
    }
}
