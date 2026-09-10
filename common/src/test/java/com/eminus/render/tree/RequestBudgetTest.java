package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RequestBudgetTest {
    @Test
    void theBudgetShrinksByTheBacklogAndStopsAtZero() {
        assertEquals(RequestBudget.MAX_PER_WALK, RequestBudget.perWalk(0));
        assertEquals(RequestBudget.MAX_PER_WALK - 10, RequestBudget.perWalk(10));
        assertEquals(0, RequestBudget.perWalk(RequestBudget.MAX_PER_WALK));
        assertEquals(0, RequestBudget.perWalk(RequestBudget.MAX_PER_WALK * 4));
    }
}
