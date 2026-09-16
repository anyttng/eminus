package com.eminus.client.cell;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.eminus.cell.CellFrame;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellCache;
import com.eminus.cell.cache.CellHandle;
import com.eminus.client.session.ClientSession;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class LightReading {
    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;
    public static final int STATE_ID = 3;
    public static final int SKY = 4;
    public static final int BLOCK = 5;
    public static final int WIDTH = 6;

    public static @Nullable CompletableFuture<List<long[]>> start(int minX, int minY, int minZ, int maxX, int maxY,
            int maxZ) {
        DimensionRuntime runtime = ClientSession.runtime();
        EminusInstance instance = ClientSession.instance();
        if (runtime == null || instance == null) {
            return null;
        }

        CompletableFuture<List<long[]>> rows = new CompletableFuture<>();
        instance.build().enqueue(scratch -> {
            try {
                rows.complete(read(runtime, minX, minY, minZ, maxX, maxY, maxZ));
            } catch (RuntimeException failure) {
                rows.completeExceptionally(failure);
            }
        });
        return rows;
    }

    private static List<long[]> read(DimensionRuntime runtime, int minX, int minY, int minZ, int maxX, int maxY,
            int maxZ) {
        CellFrame cells = runtime.frame();
        CellCache cache = runtime.cells();
        Long2ObjectOpenHashMap<CellHandle> open = new Long2ObjectOpenHashMap<>();
        List<long[]> rows = new ArrayList<>();

        try {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        CellHandle handle = open.computeIfAbsent(cells.keyAt(DetailLevel.MIN, x, y, z), cache::open);
                        int voxelX = cells.voxelX(x, DetailLevel.MIN);
                        int voxelY = cells.voxelY(y, DetailLevel.MIN);
                        int voxelZ = cells.voxelZ(z, DetailLevel.MIN);
                        long entry = handle.withCell(cell -> cell.get(voxelX, voxelY, voxelZ));
                        long[] row = new long[WIDTH];
                        row[X] = x;
                        row[Y] = y;
                        row[Z] = z;
                        row[STATE_ID] = VoxelEntry.state(entry);
                        row[SKY] = VoxelEntry.skyLight(entry);
                        row[BLOCK] = VoxelEntry.blockLight(entry);
                        rows.add(row);
                    }
                }
            }
        } finally {
            for (CellHandle handle : open.values()) {
                cache.release(handle);
            }
        }

        return rows;
    }

    private LightReading() {
    }
}
