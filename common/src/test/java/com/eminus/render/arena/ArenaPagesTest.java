package com.eminus.render.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ArenaPagesTest {
    private static final int SEGMENTS_PER_PAGE = 4;
    private static final int SEGMENTS = 16;
    private static final List<Run> NONE = List.of();

    private record Run(int first, int count) {
    }

    private static ArenaPages pages() {
        return new ArenaPages(SEGMENTS, SEGMENTS_PER_PAGE);
    }

    private static List<Run> place(ArenaPages pages, int segment, int segments) {
        List<Run> committed = new ArrayList<>();
        pages.place(segment, segments, (first, count) -> committed.add(new Run(first, count)));
        return committed;
    }

    private static List<Run> endStep(ArenaPages pages) {
        List<Run> decommitted = new ArrayList<>();
        pages.drainEmptied((first, count) -> decommitted.add(new Run(first, count)));
        return decommitted;
    }

    @Test
    void aRangeInsideAnEmptyPageCommitsThatPageAlone() {
        ArenaPages pages = pages();

        assertEquals(List.of(new Run(1, 1)), place(pages, 5, 2));
        assertEquals(1, pages.committed());
    }

    @Test
    void aSecondRangeInAnAlreadyCommittedPageCommitsNothing() {
        ArenaPages pages = pages();
        place(pages, 4, 1);

        assertEquals(NONE, place(pages, 6, 2));
        assertEquals(1, pages.committed());
    }

    @Test
    void aRangeOverPagesCommitsOnlyThePagesThatWereEmpty() {
        ArenaPages pages = pages();
        place(pages, 0, 3);

        assertEquals(List.of(new Run(1, 2)), place(pages, 3, 9));
        assertEquals(3, pages.committed());
    }

    @Test
    void replacingAMeshThatHoldsItsPagesAloneCommitsAndDecommitsNothing() {
        ArenaPages pages = pages();
        place(pages, 8, 6);

        pages.free(8, 6);

        assertEquals(NONE, place(pages, 8, 5));
        assertEquals(NONE, endStep(pages));
        assertEquals(2, pages.committed());
    }

    @Test
    void aFreedMeshThatIsNotReplacedLosesItsPagesAtTheEndOfTheStep() {
        ArenaPages pages = pages();
        place(pages, 8, 6);

        pages.free(8, 6);

        assertEquals(2, pages.committed());
        assertEquals(List.of(new Run(2, 2)), endStep(pages));
        assertEquals(0, pages.committed());
    }

    @Test
    void aRangeOverAPageEmptiedInThisStepCommitsThePagesAroundItAlone() {
        ArenaPages pages = pages();
        place(pages, 4, 4);

        pages.free(4, 4);

        assertEquals(List.of(new Run(0, 1), new Run(2, 1)), place(pages, 0, 12));
        assertEquals(NONE, endStep(pages));
        assertEquals(3, pages.committed());
    }

    @Test
    void aPageEmptiedTwiceInOneStepIsDecommittedOnce() {
        ArenaPages pages = pages();
        place(pages, 8, 2);
        pages.free(8, 2);
        place(pages, 8, 1);

        pages.free(8, 1);

        assertEquals(List.of(new Run(2, 1)), endStep(pages));
        assertEquals(0, pages.committed());
    }

    @Test
    void theEndOfAStepForgetsThePagesItWasHandedBefore() {
        ArenaPages pages = pages();
        place(pages, 8, 2);
        pages.free(8, 2);
        endStep(pages);

        assertEquals(NONE, endStep(pages));
    }

    @Test
    void freeingSegmentsOfAPageThatStillHoldsOthersDecommitsNothing() {
        ArenaPages pages = pages();
        place(pages, 8, 1);
        place(pages, 9, 3);

        pages.free(9, 3);

        assertEquals(NONE, endStep(pages));
        assertEquals(1, pages.committed());
    }

    @Test
    void freeingARangeDecommitsTheInteriorPagesAndKeepsTheSharedEdges() {
        ArenaPages pages = pages();
        place(pages, 0, 2);
        place(pages, 2, 12);
        place(pages, 14, 2);

        pages.free(2, 12);

        assertEquals(List.of(new Run(1, 2)), endStep(pages));
        assertEquals(2, pages.committed());
    }

    @Test
    void theLastPageMayBeShorterThanTheOthers() {
        ArenaPages pages = new ArenaPages(SEGMENTS + 1, SEGMENTS_PER_PAGE);

        assertEquals(5, pages.pages());
        assertEquals(List.of(new Run(4, 1)), place(pages, SEGMENTS, 1));
    }

    @Test
    void freeingASegmentThatWasNeverPlacedIsRefused() {
        ArenaPages pages = pages();

        assertThrows(IllegalStateException.class, () -> pages.free(0, 1));
    }
}
