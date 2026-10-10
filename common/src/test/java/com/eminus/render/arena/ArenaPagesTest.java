package com.eminus.render.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArenaPagesTest {
    private static final int BLOCKS_PER_PAGE = 4;
    private static final int BLOCKS = 16;

    private static ArenaPages pages() {
        return new ArenaPages(BLOCKS, BLOCKS_PER_PAGE);
    }

    @Test
    void aRangeInsideAnEmptyPageCommitsThatPageAlone() {
        ArenaPages pages = pages();

        assertEquals(new ArenaPages.PageRun(1, 1), pages.place(5, 2));
        assertEquals(1, pages.committed());
    }

    @Test
    void aSecondRangeInAnAlreadyCommittedPageCommitsNothing() {
        ArenaPages pages = pages();
        pages.place(4, 1);

        assertTrue(pages.place(6, 2).isEmpty());
        assertEquals(1, pages.committed());
    }

    @Test
    void aRangeOverPagesCommitsOnlyThePagesThatWereEmpty() {
        ArenaPages pages = pages();
        pages.place(0, 3);

        assertEquals(new ArenaPages.PageRun(1, 2), pages.place(3, 9));
        assertEquals(3, pages.committed());
    }

    @Test
    void freeingTheLastBlockOfAPageDecommitsIt() {
        ArenaPages pages = pages();
        pages.place(8, 2);

        assertEquals(new ArenaPages.PageRun(2, 1), pages.free(8, 2));
        assertEquals(0, pages.committed());
    }

    @Test
    void freeingBlocksOfAPageThatStillHoldsOthersDecommitsNothing() {
        ArenaPages pages = pages();
        pages.place(8, 1);
        pages.place(9, 3);

        assertTrue(pages.free(9, 3).isEmpty());
        assertEquals(1, pages.committed());
    }

    @Test
    void freeingARangeDecommitsTheInteriorPagesAndKeepsTheSharedEdges() {
        ArenaPages pages = pages();
        pages.place(0, 2);
        pages.place(2, 12);
        pages.place(14, 2);

        assertEquals(new ArenaPages.PageRun(1, 2), pages.free(2, 12));
        assertEquals(2, pages.committed());
    }

    @Test
    void theLastPageMayBeShorterThanTheOthers() {
        ArenaPages pages = new ArenaPages(BLOCKS + 1, BLOCKS_PER_PAGE);

        assertEquals(5, pages.pages());
        assertEquals(new ArenaPages.PageRun(4, 1), pages.place(BLOCKS, 1));
    }

    @Test
    void freeingABlockThatWasNeverPlacedIsRefused() {
        ArenaPages pages = pages();

        assertThrows(IllegalStateException.class, () -> pages.free(0, 1));
    }
}
