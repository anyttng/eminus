package com.eminus.ingest.saved;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.level.ChunkPos;

public final class ImportTiles {
    public static final int TILE_BITS = 4;
    public static final int TILE_CHUNKS = 1 << TILE_BITS;
    public static final int REGION_BITS = 5;
    public static final int READ_BORDER_CHUNKS = SavedColumn.NEIGHBOR_REACH;

    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int REGION_X_GROUP = 1;
    private static final int REGION_Z_GROUP = 2;

    public static OptionalLong regionOf(String fileName) {
        Matcher matcher = REGION_FILE.matcher(fileName);
        if (!matcher.matches()) {
            return OptionalLong.empty();
        }

        try {
            return OptionalLong.of(ChunkPos.pack(Integer.parseInt(matcher.group(REGION_X_GROUP)),
                    Integer.parseInt(matcher.group(REGION_Z_GROUP))));
        } catch (NumberFormatException tooLong) {
            return OptionalLong.empty();
        }
    }

    public static long regionOfChunk(int chunkX, int chunkZ) {
        return ChunkPos.pack(chunkX >> REGION_BITS, chunkZ >> REGION_BITS);
    }

    public static long[] plan(LongSet regions, int centerChunkX, int centerChunkZ, int radiusChunks) {
        LongArrayList tiles = new LongArrayList();
        int minTileX = (centerChunkX - radiusChunks) >> TILE_BITS;
        int maxTileX = (centerChunkX + radiusChunks) >> TILE_BITS;
        int minTileZ = (centerChunkZ - radiusChunks) >> TILE_BITS;
        int maxTileZ = (centerChunkZ + radiusChunks) >> TILE_BITS;
        for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                if (regions.contains(regionOfChunk(tileX << TILE_BITS, tileZ << TILE_BITS))) {
                    tiles.add(ChunkPos.pack(tileX, tileZ));
                }
            }
        }

        return nearestFirst(tiles, centerChunkX, centerChunkZ);
    }

    public static long[] nearestFirst(LongSet tiles, int centerChunkX, int centerChunkZ, int radiusChunks) {
        LongArrayList inReach = new LongArrayList();
        for (long tile : tiles) {
            int tileX = ChunkPos.getX(tile);
            int tileZ = ChunkPos.getZ(tile);
            if (tileX >= (centerChunkX - radiusChunks) >> TILE_BITS
                    && tileX <= (centerChunkX + radiusChunks) >> TILE_BITS
                    && tileZ >= (centerChunkZ - radiusChunks) >> TILE_BITS
                    && tileZ <= (centerChunkZ + radiusChunks) >> TILE_BITS) {
                inReach.add(tile);
            }
        }

        inReach.sort(Long::compare);
        return nearestFirst(inReach, centerChunkX, centerChunkZ);
    }

    public static long[] tilesReading(int chunkX, int chunkZ) {
        int[] tilesX = tilesAlong(chunkX);
        int[] tilesZ = tilesAlong(chunkZ);
        long[] tiles = new long[tilesX.length * tilesZ.length];
        int index = 0;
        for (int tileZ : tilesZ) {
            for (int tileX : tilesX) {
                tiles[index++] = ChunkPos.pack(tileX, tileZ);
            }
        }

        return tiles;
    }

    private static int[] tilesAlong(int chunk) {
        int tile = chunk >> TILE_BITS;
        int inTile = chunk & (TILE_CHUNKS - 1);
        if (inTile < READ_BORDER_CHUNKS) {
            return new int[] {tile, tile - 1};
        }

        return inTile >= TILE_CHUNKS - READ_BORDER_CHUNKS ? new int[] {tile, tile + 1} : new int[] {tile};
    }

    private static long[] nearestFirst(LongArrayList tiles, int centerChunkX, int centerChunkZ) {
        tiles.sort((first, second) -> Long.compare(distanceSquared(first, centerChunkX, centerChunkZ),
                distanceSquared(second, centerChunkX, centerChunkZ)));
        return tiles.toLongArray();
    }

    private static long distanceSquared(long tile, int centerChunkX, int centerChunkZ) {
        long dx = ((long) ChunkPos.getX(tile) << TILE_BITS) + TILE_CHUNKS / 2 - centerChunkX;
        long dz = ((long) ChunkPos.getZ(tile) << TILE_BITS) + TILE_CHUNKS / 2 - centerChunkZ;
        return dx * dx + dz * dz;
    }

    private ImportTiles() {
    }
}
