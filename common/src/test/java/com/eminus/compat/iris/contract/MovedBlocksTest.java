package com.eminus.compat.iris.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.ints.IntSet;

import org.junit.jupiter.api.Test;

class MovedBlocksTest {
    private static final int WATER = 10001;
    private static final int SMALL_PLANTS = 10002;
    private static final int LEAVES = 10005;

    @Test
    void idsSeparatedByAnyWhitespaceAreRead() {
        assertEquals(IntSet.of(WATER, SMALL_PLANTS, LEAVES), MovedBlocks.declared("moved =  10001  10002\t10005 \n"));
    }

    @Test
    void aListContinuedOverTheBlankLinesOfDroppedIfdefsReadsWhole() {
        assertEquals(IntSet.of(WATER, LEAVES), MovedBlocks.declared("moved = \\\n\n10001 \\\n\n\n10005 \\\n"));
    }

    @Test
    void aFileWithoutTheKeyMovesNothing() {
        assertEquals(IntSet.of(), MovedBlocks.declared("other = 10001\n"));
    }

    @Test
    void aTokenThatIsNoIdIsSkippedAndTheRestKept() {
        assertEquals(IntSet.of(WATER, LEAVES), MovedBlocks.parse("10001 leaves 10005"));
    }
}
