package com.eminus.render.tree;

import com.eminus.settings.FarDistance;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class TreeRing {
    public static final int MOVE_BLOCKS = 128;
    public static final int COLUMNS_PER_UPDATE = 64;

    private static final int NO_RADIUS = -1;
    private static final long MOVE_BLOCKS_SQUARED = (long) MOVE_BLOCKS * MOVE_BLOCKS;
    private static final long LOW_MASK = 0xFFFF_FFFFL;

    public interface Columns {
        void added(int cellX, int cellZ);

        void removed(int cellX, int cellZ);
    }

    private final LongOpenHashSet present = new LongOpenHashSet();
    private final LongArrayList toAdd = new LongArrayList();
    private final LongArrayList toRemove = new LongArrayList();

    private double anchorX;
    private double anchorZ;
    private int radius = NO_RADIUS;
    private int centreX;
    private int centreZ;

    public boolean update(double eyeX, double eyeZ, int radius, Columns columns) {
        if (this.radius != radius || moved(eyeX, eyeZ)) {
            recentre(eyeX, eyeZ, radius);
        }

        return drain(columns);
    }

    public int columns() {
        return present.size();
    }

    public boolean pending() {
        return !toAdd.isEmpty() || !toRemove.isEmpty();
    }

    static long column(int cellX, int cellZ) {
        return ((long) cellX << Integer.SIZE) | (cellZ & LOW_MASK);
    }

    static int cellX(long column) {
        return (int) (column >> Integer.SIZE);
    }

    static int cellZ(long column) {
        return (int) column;
    }

    private boolean moved(double eyeX, double eyeZ) {
        double dx = eyeX - anchorX;
        double dz = eyeZ - anchorZ;
        return dx * dx + dz * dz >= MOVE_BLOCKS_SQUARED;
    }

    private void recentre(double eyeX, double eyeZ, int radius) {
        anchorX = eyeX;
        anchorZ = eyeZ;
        this.radius = radius;
        centreX = Math.floorDiv((int) Math.floor(eyeX), FarDistance.BLOCKS_PER_TOP_LEVEL_CELL);
        centreZ = Math.floorDiv((int) Math.floor(eyeZ), FarDistance.BLOCKS_PER_TOP_LEVEL_CELL);
        toAdd.clear();
        toRemove.clear();

        for (int cellX = centreX - radius; cellX <= centreX + radius; cellX++) {
            for (int cellZ = centreZ - radius; cellZ <= centreZ + radius; cellZ++) {
                long column = column(cellX, cellZ);
                if (inDisc(cellX, cellZ) && !present.contains(column)) {
                    toAdd.add(column);
                }
            }
        }

        LongIterator kept = present.iterator();
        while (kept.hasNext()) {
            long column = kept.nextLong();
            if (!inDisc(cellX(column), cellZ(column))) {
                toRemove.add(column);
            }
        }

        toAdd.sort((LongComparator) (left, right) -> Long.compare(distanceSquared(right), distanceSquared(left)));
    }

    private boolean inDisc(int cellX, int cellZ) {
        int dx = cellX - centreX;
        int dz = cellZ - centreZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    private long distanceSquared(long column) {
        long dx = cellX(column) - centreX;
        long dz = cellZ(column) - centreZ;
        return dx * dx + dz * dz;
    }

    private boolean drain(Columns columns) {
        int budget = COLUMNS_PER_UPDATE;
        boolean changed = false;

        while (budget > 0 && !toRemove.isEmpty()) {
            long column = toRemove.removeLong(toRemove.size() - 1);
            present.remove(column);
            columns.removed(cellX(column), cellZ(column));
            budget--;
            changed = true;
        }

        while (budget > 0 && !toAdd.isEmpty()) {
            long column = toAdd.removeLong(toAdd.size() - 1);
            present.add(column);
            columns.added(cellX(column), cellZ(column));
            budget--;
            changed = true;
        }

        return changed;
    }
}
