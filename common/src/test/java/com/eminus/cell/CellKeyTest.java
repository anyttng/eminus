package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CellKeyTest {
    private static final int[] HORIZONTALS =
            {CellKey.MIN_HORIZONTAL, -1_000_000, -1, 0, 1, 1_000_000, CellKey.MAX_HORIZONTAL};
    private static final int[] VERTICALS = {CellKey.MIN_VERTICAL, -1, 0, 1, CellKey.MAX_VERTICAL};

    @Test
    void everyLevelAndSignedCoordinateRoundTrips() {
        for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
            for (int x : HORIZONTALS) {
                for (int y : VERTICALS) {
                    for (int z : HORIZONTALS) {
                        long key = CellKey.pack(level, x, y, z);

                        assertEquals(level, CellKey.level(key));
                        assertEquals(x, CellKey.x(key));
                        assertEquals(y, CellKey.y(key));
                        assertEquals(z, CellKey.z(key));
                    }
                }
            }
        }
    }

    @Test
    void everyKeyStaysNonNegative() {
        for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
            for (int x : HORIZONTALS) {
                for (int y : VERTICALS) {
                    for (int z : HORIZONTALS) {
                        assertTrue(CellKey.pack(level, x, y, z) >= 0);
                    }
                }
            }
        }
    }

    @Test
    void keysOrderByLevelThenPosition() {
        long[] ordered = {
                CellKey.pack(0, -1, 0, 0),
                CellKey.pack(0, 0, -1, -1),
                CellKey.pack(0, 0, 0, -1),
                CellKey.pack(0, 0, -1, 0),
                CellKey.pack(0, 0, 0, 0),
                CellKey.pack(0, 0, 1, 0),
                CellKey.pack(0, 1, 0, 0),
                CellKey.pack(1, -1, 0, 0),
                CellKey.pack(DetailLevel.MAX, CellKey.MAX_HORIZONTAL, CellKey.MAX_VERTICAL, CellKey.MAX_HORIZONTAL),
        };

        for (int index = 1; index < ordered.length; index++) {
            assertTrue(ordered[index - 1] < ordered[index], "key " + index + " does not follow its predecessor");
        }
    }
}
