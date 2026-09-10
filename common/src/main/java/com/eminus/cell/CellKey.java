package com.eminus.cell;

import net.minecraft.core.Direction;

public final class CellKey {
    public static final int HORIZONTAL_BITS = 24;
    public static final int VERTICAL_BITS = 12;
    public static final int MIN_HORIZONTAL = -(1 << (HORIZONTAL_BITS - 1));
    public static final int MAX_HORIZONTAL = (1 << (HORIZONTAL_BITS - 1)) - 1;
    public static final int MIN_VERTICAL = -(1 << (VERTICAL_BITS - 1));
    public static final int MAX_VERTICAL = (1 << (VERTICAL_BITS - 1)) - 1;

    private static final int LEVEL_BITS = 3;
    private static final int Y_SHIFT = 0;
    private static final int Z_SHIFT = Y_SHIFT + VERTICAL_BITS;
    private static final int X_SHIFT = Z_SHIFT + HORIZONTAL_BITS;
    private static final int LEVEL_SHIFT = X_SHIFT + HORIZONTAL_BITS;
    private static final long LEVEL_MASK = (1L << LEVEL_BITS) - 1;
    private static final long HORIZONTAL_MASK = (1L << HORIZONTAL_BITS) - 1;
    private static final long VERTICAL_MASK = (1L << VERTICAL_BITS) - 1;

    public static long pack(int level, int x, int y, int z) {
        return ((level & LEVEL_MASK) << LEVEL_SHIFT)
                | ((biasHorizontal(x) & HORIZONTAL_MASK) << X_SHIFT)
                | ((biasHorizontal(z) & HORIZONTAL_MASK) << Z_SHIFT)
                | ((biasVertical(y) & VERTICAL_MASK) << Y_SHIFT);
    }

    public static int level(long key) {
        return (int) ((key >>> LEVEL_SHIFT) & LEVEL_MASK);
    }

    public static int x(long key) {
        return (int) ((key >>> X_SHIFT) & HORIZONTAL_MASK) + MIN_HORIZONTAL;
    }

    public static int y(long key) {
        return (int) ((key >>> Y_SHIFT) & VERTICAL_MASK) + MIN_VERTICAL;
    }

    public static int z(long key) {
        return (int) ((key >>> Z_SHIFT) & HORIZONTAL_MASK) + MIN_HORIZONTAL;
    }

    public static long neighbour(long key, Direction face) {
        int level = level(key);
        int step = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;

        return switch (face.getAxis()) {
            case X -> pack(level, x(key) + step, y(key), z(key));
            case Y -> pack(level, x(key), y(key) + step, z(key));
            case Z -> pack(level, x(key), y(key), z(key) + step);
        };
    }

    private static long biasHorizontal(int coordinate) {
        return (long) coordinate - MIN_HORIZONTAL;
    }

    private static long biasVertical(int coordinate) {
        return (long) coordinate - MIN_VERTICAL;
    }

    private CellKey() {
    }
}
