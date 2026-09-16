package com.eminus.cell;

public final class CellFrame {
    private final int minBlockY;

    public CellFrame(int minBlockY) {
        this.minBlockY = minBlockY;
    }

    public int minBlockY() {
        return minBlockY;
    }

    public int cellX(int blockX, int level) {
        return Math.floorDiv(blockX, DetailLevel.blocksPerCell(level));
    }

    public int cellY(int blockY, int level) {
        return Math.floorDiv(blockY - minBlockY, DetailLevel.blocksPerCell(level));
    }

    public int cellZ(int blockZ, int level) {
        return Math.floorDiv(blockZ, DetailLevel.blocksPerCell(level));
    }

    public long keyAt(int level, int blockX, int blockY, int blockZ) {
        return CellKey.pack(level, cellX(blockX, level), cellY(blockY, level), cellZ(blockZ, level));
    }

    public int voxelX(int blockX, int level) {
        return Math.floorMod(blockX, DetailLevel.blocksPerCell(level)) >> level;
    }

    public int voxelY(int blockY, int level) {
        return Math.floorMod(blockY - minBlockY, DetailLevel.blocksPerCell(level)) >> level;
    }

    public int voxelZ(int blockZ, int level) {
        return Math.floorMod(blockZ, DetailLevel.blocksPerCell(level)) >> level;
    }

    public int originBlockX(int cellX, int level) {
        return cellX * DetailLevel.blocksPerCell(level);
    }

    public int originBlockY(int cellY, int level) {
        return cellY * DetailLevel.blocksPerCell(level) + minBlockY;
    }

    public int originBlockZ(int cellZ, int level) {
        return cellZ * DetailLevel.blocksPerCell(level);
    }

    public int blockX(int cellX, int voxelX, int level) {
        return originBlockX(cellX, level) + (voxelX << level);
    }

    public int blockY(int cellY, int voxelY, int level) {
        return originBlockY(cellY, level) + (voxelY << level);
    }

    public int blockZ(int cellZ, int voxelZ, int level) {
        return originBlockZ(cellZ, level) + (voxelZ << level);
    }

    public int blockXOf(long key, int voxelX) {
        return blockX(CellKey.x(key), voxelX, CellKey.level(key));
    }

    public int blockYOf(long key, int voxelY) {
        return blockY(CellKey.y(key), voxelY, CellKey.level(key));
    }

    public int blockZOf(long key, int voxelZ) {
        return blockZ(CellKey.z(key), voxelZ, CellKey.level(key));
    }
}
