package com.eminus.mesh;

import java.util.Arrays;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class TintBlend {
    public static final int MAX_RADIUS = 7;
    public static final int NO_COLOUR = BiomeTints.NO_COLOUR;

    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int MAX_WINDOW = SIDE + 2 * MAX_RADIUS;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL_MASK = 0xFF;

    private static final class RowLayers {
        private final int[][] colours = new int[SIDE][SIDE * SIDE];
        private long built;
    }

    private final Int2ObjectMap<RowLayers> rows = new Int2ObjectOpenHashMap<>();
    private final int[] red = new int[(MAX_WINDOW + 1) * (MAX_WINDOW + 1)];
    private final int[] green = new int[red.length];
    private final int[] blue = new int[red.length];
    private final int[] count = new int[red.length];

    private CellVoxels voxels;
    private BiomeTints tints;
    private int level;
    private int originX;
    private int originZ;
    private int radius;

    public static int columns(int blendRadius, int level) {
        return (blendRadius + DetailLevel.blocksPerVoxel(level) - 1) >> level;
    }

    public void begin(CellVoxels voxels, BiomeTints tints, long key, int blendRadius) {
        this.voxels = voxels;
        this.tints = tints;
        level = CellKey.level(key);
        originX = CellKey.x(key) * DetailLevel.blocksPerCell(level);
        originZ = CellKey.z(key) * DetailLevel.blocksPerCell(level);
        radius = columns(blendRadius, level);

        for (RowLayers layers : rows.values()) {
            layers.built = 0L;
        }
    }

    public int colour(int row, int x, int y, int z) {
        RowLayers layers = rows.computeIfAbsent(row, unused -> new RowLayers());
        long bit = 1L << y;
        if ((layers.built & bit) == 0L) {
            build(row, y, layers.colours[y]);
            layers.built |= bit;
        }

        return layers.colours[y][z * SIDE + x];
    }

    private void build(int row, int y, int[] into) {
        int window = SIDE + 2 * radius;
        int stride = window + 1;
        clearBorders(stride, window);
        int lastBiome = VoxelEntry.UNKNOWN_BIOME;
        int lastColour = NO_COLOUR;
        boolean lastPositional = false;

        for (int wz = 0; wz < window; wz++) {
            int rowRed = 0;
            int rowGreen = 0;
            int rowBlue = 0;
            int rowCount = 0;

            for (int wx = 0; wx < window; wx++) {
                int x = wx - radius;
                int z = wz - radius;
                int biome = voxels.biome(x, y, z);
                int colour = NO_COLOUR;

                if (biome != VoxelEntry.UNKNOWN_BIOME) {
                    if (biome != lastBiome) {
                        lastBiome = biome;
                        lastPositional = tints.positional(biome);
                        lastColour = lastPositional ? NO_COLOUR : sample(row, biome, x, z);
                    }

                    colour = lastPositional ? sample(row, biome, x, z) : lastColour;
                }

                if (colour != NO_COLOUR) {
                    rowRed += (colour >> RED_SHIFT) & CHANNEL_MASK;
                    rowGreen += (colour >> GREEN_SHIFT) & CHANNEL_MASK;
                    rowBlue += colour & CHANNEL_MASK;
                    rowCount++;
                }

                int at = (wz + 1) * stride + wx + 1;
                red[at] = red[at - stride] + rowRed;
                green[at] = green[at - stride] + rowGreen;
                blue[at] = blue[at - stride] + rowBlue;
                count[at] = count[at - stride] + rowCount;
            }
        }

        int span = 2 * radius + 1;
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                int samples = sum(count, stride, x, z, span);
                into[z * SIDE + x] = samples == 0
                        ? NO_COLOUR
                        : (sum(red, stride, x, z, span) / samples) << RED_SHIFT
                                | (sum(green, stride, x, z, span) / samples) << GREEN_SHIFT
                                | sum(blue, stride, x, z, span) / samples;
            }
        }
    }

    private void clearBorders(int stride, int window) {
        for (int[] table : new int[][] {red, green, blue, count}) {
            Arrays.fill(table, 0, stride, 0);
            for (int wz = 1; wz <= window; wz++) {
                table[wz * stride] = 0;
            }
        }
    }

    private int sample(int row, int biome, int x, int z) {
        return tints.colour(row, biome, originX + (x << level), originZ + (z << level));
    }

    private static int sum(int[] table, int stride, int x, int z, int span) {
        int x1 = x + span;
        int z1 = z + span;
        return table[z1 * stride + x1] - table[z * stride + x1] - table[z1 * stride + x] + table[z * stride + x];
    }
}
