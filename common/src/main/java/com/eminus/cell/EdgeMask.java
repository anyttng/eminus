package com.eminus.cell;

public final class EdgeMask {
    public static final int NONE = 0;

    private static final int SPAN = 3;

    public static int bit(int stepX, int stepY, int stepZ) {
        return 1 << slot(stepX, stepY, stepZ);
    }

    public static int stepX(int slot) {
        return slot % SPAN - 1;
    }

    public static int stepY(int slot) {
        return slot / SPAN % SPAN - 1;
    }

    public static int stepZ(int slot) {
        return slot / (SPAN * SPAN) - 1;
    }

    public static int of(int faceMask) {
        int stepX = step(faceMask, FaceMask.WEST, FaceMask.EAST);
        int stepZ = step(faceMask, FaceMask.NORTH, FaceMask.SOUTH);
        int mask = NONE;

        if (stepX != 0 && stepZ != 0) {
            mask |= bit(stepX, 0, stepZ);
        }

        if ((faceMask & FaceMask.DOWN) != 0) {
            if (stepX != 0) {
                mask |= bit(stepX, -1, 0);
            }

            if (stepZ != 0) {
                mask |= bit(0, -1, stepZ);
            }

            if (stepX != 0 && stepZ != 0) {
                mask |= bit(stepX, -1, stepZ);
            }
        }

        return mask;
    }

    private static int step(int faceMask, int low, int high) {
        if ((faceMask & low) != 0) {
            return -1;
        }

        return (faceMask & high) != 0 ? 1 : 0;
    }

    private static int slot(int stepX, int stepY, int stepZ) {
        return (stepX + 1) + SPAN * (stepY + 1) + SPAN * SPAN * (stepZ + 1);
    }

    private EdgeMask() {
    }
}
