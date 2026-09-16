package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;

import org.junit.jupiter.api.Test;

class TintBlendTest {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int LAST = SIDE - 1;
    private static final int ROW = 0;
    private static final int PLAINS = 1;
    private static final int SWAMP = 2;
    private static final int PLAINS_GREEN = 0x91BD59;
    private static final int SWAMP_GREEN = 0x6A7039;
    private static final int STONE = 1;
    private static final int LAYER = 5;
    private static final int GAME_RADIUS = 2;
    private static final int NO_RADIUS = 0;
    private static final int BORDER = 16;
    private static final int MIDDLE = 16;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int BLUE_SHIFT = 0;
    private static final int CHANNEL_MASK = 0xFF;
    private static final long KEY = CellKey.pack(DetailLevel.MIN, 0, 0, 0);

    private final FakeTints tints = new FakeTints();
    private final CellVoxels voxels = new CellVoxels();
    private final TintBlend blend = new TintBlend();

    TintBlendTest() {
        tints.define(ROW, PLAINS, PLAINS_GREEN);
        tints.define(ROW, SWAMP, SWAMP_GREEN);
    }

    @Test
    void theRadiusShrinksWithTheLevelAndStaysAtLeastOneColumn() {
        assertEquals(2, TintBlend.columns(GAME_RADIUS, 0));
        assertEquals(1, TintBlend.columns(GAME_RADIUS, 1));
        assertEquals(1, TintBlend.columns(GAME_RADIUS, DetailLevel.MAX));
        assertEquals(4, TintBlend.columns(TintBlend.MAX_RADIUS, 1));
        assertEquals(0, TintBlend.columns(NO_RADIUS, DetailLevel.MAX));
    }

    @Test
    void withoutBlendAVoxelTakesItsOwnBiomesColour() {
        voxels.load(border(BORDER));
        blend.begin(voxels, tints, KEY, NO_RADIUS);

        assertEquals(PLAINS_GREEN, blend.colour(ROW, BORDER - 1, LAYER, MIDDLE));
        assertEquals(SWAMP_GREEN, blend.colour(ROW, BORDER, LAYER, MIDDLE));
    }

    @Test
    void acrossABorderTheColourIsTheGamesAverageOverTheSquare() {
        voxels.load(border(BORDER));
        blend.begin(voxels, tints, KEY, GAME_RADIUS);

        assertEquals(PLAINS_GREEN, blend.colour(ROW, BORDER - 3, LAYER, MIDDLE));
        assertEquals(gameAverage(PLAINS_GREEN, 3, SWAMP_GREEN, 2), blend.colour(ROW, BORDER - 1, LAYER, MIDDLE));
        assertEquals(gameAverage(PLAINS_GREEN, 2, SWAMP_GREEN, 3), blend.colour(ROW, BORDER, LAYER, MIDDLE));
        assertEquals(SWAMP_GREEN, blend.colour(ROW, BORDER + 2, LAYER, MIDDLE));
    }

    @Test
    void theFirstColumnIsRightAfterACellOfAnotherLevel() {
        voxels.load(filled(SWAMP, SIDE));
        blend.begin(voxels, tints, KEY, GAME_RADIUS);
        blend.colour(ROW, LAST, LAYER, MIDDLE);

        voxels.load(filled(PLAINS, SIDE));
        blend.begin(voxels, tints, CellKey.pack(DetailLevel.MIN + 1, 0, 0, 0), GAME_RADIUS);

        assertEquals(PLAINS_GREEN, blend.colour(ROW, 0, LAYER, MIDDLE));
    }

    @Test
    void theNeighbourCellsColumnsJoinTheSquareAtTheCellEdge() {
        voxels.load(filled(PLAINS, SIDE));
        voxels.loadBiomes(filled(SWAMP, SIDE), 1, 0, GAME_RADIUS);
        blend.begin(voxels, tints, KEY, GAME_RADIUS);

        assertEquals(gameAverage(PLAINS_GREEN, 3, SWAMP_GREEN, 2), blend.colour(ROW, LAST, LAYER, MIDDLE));
    }

    @Test
    void columnsWithNoIngestedBiomeAreLeftOutOfTheAverage() {
        voxels.load(filled(PLAINS, BORDER));
        blend.begin(voxels, tints, KEY, GAME_RADIUS);

        assertEquals(PLAINS_GREEN, blend.colour(ROW, BORDER - 1, LAYER, MIDDLE));
    }

    @Test
    void aBiomeWhoseColourVariesIsSampledAtEveryColumn() {
        tints.vary(SWAMP);
        voxels.load(filled(SWAMP, SIDE));
        blend.begin(voxels, tints, KEY, NO_RADIUS);

        assertNotEquals(blend.colour(ROW, MIDDLE, LAYER, MIDDLE), blend.colour(ROW, MIDDLE + 1, LAYER, MIDDLE));
    }

    private static int gameAverage(int first, int firstColumns, int second, int secondColumns) {
        int count = firstColumns + secondColumns;
        return channel(first, second, RED_SHIFT, firstColumns, secondColumns, count) << RED_SHIFT
                | channel(first, second, GREEN_SHIFT, firstColumns, secondColumns, count) << GREEN_SHIFT
                | channel(first, second, BLUE_SHIFT, firstColumns, secondColumns, count);
    }

    private static int channel(int first, int second, int shift, int firstColumns, int secondColumns, int count) {
        return (((first >> shift) & CHANNEL_MASK) * firstColumns + ((second >> shift) & CHANNEL_MASK) * secondColumns)
                / count;
    }

    private static Cell border(int at) {
        Cell cell = filled(PLAINS, at);
        for (int z = 0; z < SIDE; z++) {
            for (int x = at; x < SIDE; x++) {
                cell.set(x, LAYER, z, entry(SWAMP));
            }
        }

        return cell;
    }

    private static Cell filled(int biome, int width) {
        Cell cell = Cell.blank(KEY);
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < width; x++) {
                cell.set(x, LAYER, z, entry(biome));
            }
        }

        return cell;
    }

    private static long entry(int biome) {
        return VoxelEntry.pack(STONE, biome, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0));
    }
}
