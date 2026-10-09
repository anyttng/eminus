package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IdTableTest {
    private static final int MISSING = -7;

    @Test
    void anUnsetIdANegativeIdAndAnIdPastTheTableReadAsMissing() {
        IdTable table = new IdTable(4, MISSING);

        assertEquals(MISSING, table.get(0));
        assertEquals(MISSING, table.get(-1));
        assertEquals(MISSING, table.get(4));
    }

    @Test
    void anIdPastTheCapacityGrowsTheTableAndKeepsEveryEarlierValue() {
        IdTable table = new IdTable(0, MISSING);

        table.put(0, 10);
        table.put(1000, 20);
        table.put(1001, 30);

        assertEquals(10, table.get(0));
        assertEquals(20, table.get(1000));
        assertEquals(30, table.get(1001));
        assertEquals(MISSING, table.get(999));
    }

    @Test
    void theGrownLengthDoublesOrReachesTheIdWhicheverIsLarger() {
        assertEquals(512, IdTable.grownLength(256, 300));
        assertEquals(1001, IdTable.grownLength(256, 1000));
        assertEquals(1, IdTable.grownLength(0, 0));
    }
}
