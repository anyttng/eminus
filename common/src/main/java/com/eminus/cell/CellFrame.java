package com.eminus.cell;

public final class CellFrame {
    private final int minBlockY;

    public CellFrame(int minBlockY) {
        this.minBlockY = minBlockY;
    }

    public int minBlockY() {
        return minBlockY;
    }

    public static int cellX(int blockX, int level) {
        return Math.floorDiv(blockX, DetailLevel.blocksPerCell(level));
    }

    public int cellY(int blockY, int level) {
        return Math.floorDiv(blockY - minBlockY, DetailLevel.blocksPerCell(level));
    }

    public static int cellZ(int blockZ, int level) {
        return Math.floorDiv(blockZ, DetailLevel.blocksPerCell(level));
    }

    public long keyAt(int level, int blockX, int blockY, int blockZ) {
        return CellKey.pack(level, cellX(blockX, level), cellY(blockY, level), cellZ(blockZ, level));
    }

    public static int voxelX(int blockX, int level) {
        return Math.floorMod(blockX, DetailLevel.blocksPerCell(level)) >> level;
    }

    public int voxelY(int blockY, int level) {
        return Math.floorMod(blockY - minBlockY, DetailLevel.blocksPerCell(level)) >> level;
    }

    public static int voxelZ(int blockZ, int level) {
        return Math.floorMod(blockZ, DetailLevel.blocksPerCell(level)) >> level;
    }

    public static int originBlockX(int cellX, int level) {
        return cellX * DetailLevel.blocksPerCell(level);
    }

    public int originBlockY(int cellY, int level) {
        return cellY * DetailLevel.blocksPerCell(level) + minBlockY;
    }

    public static int originBlockZ(int cellZ, int level) {
        return cellZ * DetailLevel.blocksPerCell(level);
    }

    public static int originXOf(long key) {
        return originBlockX(CellKey.x(key), CellKey.level(key));
    }

    public int originYOf(long key) {
        return originBlockY(CellKey.y(key), CellKey.level(key));
    }

    public static int originZOf(long key) {
        return originBlockZ(CellKey.z(key), CellKey.level(key));
    }

    public static int blockX(int cellX, int voxelX, int level) {
        return originBlockX(cellX, level) + (voxelX << level);
    }

    public int blockY(int cellY, int voxelY, int level) {
        return originBlockY(cellY, level) + (voxelY << level);
    }

    public static int blockZ(int cellZ, int voxelZ, int level) {
        return originBlockZ(cellZ, level) + (voxelZ << level);
    }

    public static int blockXOf(long key, int voxelX) {
        return blockX(CellKey.x(key), voxelX, CellKey.level(key));
    }

    public int blockYOf(long key, int voxelY) {
        return blockY(CellKey.y(key), voxelY, CellKey.level(key));
    }

    public static int blockZOf(long key, int voxelZ) {
        return blockZ(CellKey.z(key), voxelZ, CellKey.level(key));
    }
}
