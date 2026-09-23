package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import com.eminus.cell.DetailLevel;

import org.junit.jupiter.api.Test;

class GreedyMergerTest {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final long STONE = Quad.data(0xF0, 7, 3);
    private static final long GLASS = Quad.data(0xF0, 9, 3);

    private record Run(int u, int v, int width, int height, long data) {
    }

    @Test
    void oneFaceComesBackAsOneQuadOfASingleVoxel() {
        FacePlane plane = new FacePlane();
        plane.clear();
        plane.set(5, 6, STONE);

        assertEquals(List.of(new Run(5, 6, 1, 1, STONE)), merge(plane));
    }

    @Test
    void aFullPlaneComesBackAsFourQuadsOfSixteenBySixteen() {
        FacePlane plane = filled(STONE);
        List<Run> runs = merge(plane);

        assertEquals(4, runs.size());
        for (Run run : runs) {
            assertEquals(Quad.MAX_SIDE, run.width());
            assertEquals(Quad.MAX_SIDE, run.height());
            assertEquals(STONE, run.data());
        }
    }

    @Test
    void aRunStopsWhereTheDataChanges() {
        FacePlane plane = new FacePlane();
        plane.clear();
        for (int u = 0; u < 6; u++) {
            plane.set(u, 0, u < 4 ? STONE : GLASS);
        }

        assertEquals(
                List.of(new Run(0, 0, 4, 1, STONE), new Run(4, 0, 2, 1, GLASS)),
                merge(plane));
    }

    @Test
    void aRunStopsAtAHoleAndPicksUpAfterIt() {
        FacePlane plane = new FacePlane();
        plane.clear();
        plane.set(0, 0, STONE);
        plane.set(1, 0, STONE);
        plane.set(3, 0, STONE);

        assertEquals(
                List.of(new Run(0, 0, 2, 1, STONE), new Run(3, 0, 1, 1, STONE)),
                merge(plane));
    }

    @Test
    void aColumnTallerThanSixteenRowsBreaksIntoTwoQuads() {
        FacePlane plane = new FacePlane();
        plane.clear();
        for (int v = 0; v < 20; v++) {
            plane.set(0, v, STONE);
        }

        assertEquals(
                List.of(new Run(0, 0, 1, Quad.MAX_SIDE, STONE), new Run(0, 16, 1, 4, STONE)),
                merge(plane));
    }

    @Test
    void rowsOfDifferentWidthsDoNotMerge() {
        FacePlane plane = new FacePlane();
        plane.clear();
        plane.set(0, 0, STONE);
        plane.set(1, 0, STONE);
        plane.set(0, 1, STONE);

        assertEquals(
                List.of(new Run(0, 0, 2, 1, STONE), new Run(0, 1, 1, 1, STONE)),
                merge(plane));
    }

    @Test
    void dataThatDoesNotStackStillMergesAlongTheRow() {
        FacePlane plane = new FacePlane();
        plane.clear();
        for (int v = 0; v < 2; v++) {
            plane.set(0, v, STONE);
            plane.set(1, v, STONE);
        }

        List<Run> runs = new ArrayList<>();
        new GreedyMerger().merge(plane, new GreedyMerger.Emitter() {
            @Override
            public void emit(int u, int v, int width, int height, long data) {
                runs.add(new Run(u, v, width, height, data));
            }

            @Override
            public boolean stacks(long data) {
                return false;
            }
        });

        assertEquals(List.of(new Run(0, 0, 2, 1, STONE), new Run(0, 1, 2, 1, STONE)), runs);
    }

    private static FacePlane filled(long data) {
        FacePlane plane = new FacePlane();
        plane.clear();

        for (int v = 0; v < SIDE; v++) {
            for (int u = 0; u < SIDE; u++) {
                plane.set(u, v, data);
            }
        }

        return plane;
    }

    private static List<Run> merge(FacePlane plane) {
        List<Run> runs = new ArrayList<>();
        new GreedyMerger().merge(plane, (u, v, width, height, data) -> runs.add(new Run(u, v, width, height, data)));
        return runs;
    }
}
