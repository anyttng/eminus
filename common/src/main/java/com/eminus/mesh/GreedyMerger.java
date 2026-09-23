package com.eminus.mesh;

import com.eminus.cell.DetailLevel;

public final class GreedyMerger {
    @FunctionalInterface
    public interface Emitter {
        void emit(int u, int v, int width, int height, long data);

        default boolean merges(long data) {
            return true;
        }
    }

    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int MAX_RUN = Quad.MAX_SIDE;

    private static final int START_SHIFT = 0;
    private static final int WIDTH_SHIFT = 5;
    private static final int ROW_SHIFT = 10;
    private static final int HEIGHT_SHIFT = 15;
    private static final int FIELD_MASK = 0x1F;

    private final int[] rowStart = new int[SIDE];
    private final int[] rowWidth = new int[SIDE];
    private final long[] rowData = new long[SIDE];

    private int[] open = new int[SIDE];
    private long[] openData = new long[SIDE];
    private int[] grown = new int[SIDE];
    private long[] grownData = new long[SIDE];

    private int openCount;
    private int rowCount;

    public void merge(FacePlane plane, Emitter emitter) {
        openCount = 0;

        for (int v = 0; v < SIDE; v++) {
            rowCount = runs(plane, v, emitter);
            match(v, emitter);
        }

        for (int index = 0; index < openCount; index++) {
            emit(open[index], openData[index], emitter);
        }
    }

    private int runs(FacePlane plane, int v, Emitter emitter) {
        int present = plane.row(v);
        int count = 0;
        int u = 0;

        while (u < SIDE) {
            if ((present & (1 << u)) == 0) {
                u++;
                continue;
            }

            long data = plane.data(u, v);
            int width = 1;
            boolean merges = emitter.merges(data);

            while (merges && width < MAX_RUN && u + width < SIDE
                    && (present & (1 << (u + width))) != 0
                    && plane.data(u + width, v) == data) {
                width++;
            }

            rowStart[count] = u;
            rowWidth[count] = width;
            rowData[count] = data;
            count++;
            u += width;
        }

        return count;
    }

    private void match(int v, Emitter emitter) {
        int grownCount = 0;
        int index = 0;

        for (int at = 0; at < rowCount; at++) {
            int start = rowStart[at];
            int width = rowWidth[at];
            long data = rowData[at];
            boolean merged = false;

            while (index < openCount && start(open[index]) <= start) {
                if (start(open[index]) == start && width(open[index]) == width
                        && openData[index] == data && height(open[index]) < MAX_RUN && emitter.merges(data)) {
                    grown[grownCount] = taller(open[index]);
                    grownData[grownCount] = data;
                    grownCount++;
                    index++;
                    merged = true;
                    break;
                }

                emit(open[index], openData[index], emitter);
                index++;
            }

            if (!merged) {
                grown[grownCount] = run(start, width, v, 1);
                grownData[grownCount] = data;
                grownCount++;
            }
        }

        while (index < openCount) {
            emit(open[index], openData[index], emitter);
            index++;
        }

        int[] carriedRuns = open;
        long[] carriedData = openData;
        open = grown;
        openData = grownData;
        grown = carriedRuns;
        grownData = carriedData;
        openCount = grownCount;
    }

    private static void emit(int run, long data, Emitter emitter) {
        emitter.emit(start(run), row(run), width(run), height(run), data);
    }

    private static int run(int start, int width, int row, int height) {
        return start << START_SHIFT | width << WIDTH_SHIFT | row << ROW_SHIFT | height << HEIGHT_SHIFT;
    }

    private static int taller(int run) {
        return run + (1 << HEIGHT_SHIFT);
    }

    private static int start(int run) {
        return run >>> START_SHIFT & FIELD_MASK;
    }

    private static int width(int run) {
        return run >>> WIDTH_SHIFT & FIELD_MASK;
    }

    private static int row(int run) {
        return run >>> ROW_SHIFT & FIELD_MASK;
    }

    private static int height(int run) {
        return run >>> HEIGHT_SHIFT & FIELD_MASK;
    }
}
