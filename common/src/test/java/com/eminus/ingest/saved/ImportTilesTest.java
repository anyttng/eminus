package com.eminus.ingest.saved;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;

import com.eminus.VanillaBootstrap;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.level.ChunkPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ImportTilesTest {
    private static final int PLAYER_CHUNK = 8;
    private static final int TILES_PER_REGION_SIDE = 2;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aRegionFileNameGivesItsRegionAndAnyOtherFileNone() {
        assertEquals(OptionalLong.of(ChunkPos.pack(-3, 12)), ImportTiles.regionOf("r.-3.12.mca"));
        assertTrue(ImportTiles.regionOf("r.1.2.mca.tmp").isEmpty());
        assertTrue(ImportTiles.regionOf("r.1.mca").isEmpty());
        assertTrue(ImportTiles.regionOf("r.99999999999.0.mca").isEmpty());
    }

    @Test
    void onlyTilesOfListedRegionsArePlannedNearestFirst() {
        LongSet regions = new LongOpenHashSet(new long[] {ChunkPos.pack(0, 0), ChunkPos.pack(-1, 0)});

        long[] tiles = ImportTiles.plan(regions, PLAYER_CHUNK, PLAYER_CHUNK, 4 * ImportTiles.TILE_CHUNKS);

        assertEquals(2 * TILES_PER_REGION_SIDE * TILES_PER_REGION_SIDE, tiles.length);
        assertArrayEquals(new long[] {
                ChunkPos.pack(0, 0), ChunkPos.pack(-1, 0), ChunkPos.pack(1, 0), ChunkPos.pack(0, 1),
                ChunkPos.pack(-1, 1), ChunkPos.pack(1, 1), ChunkPos.pack(-2, 0), ChunkPos.pack(-2, 1)}, tiles);
    }

    @Test
    void aTileOutsideTheRadiusIsNotPlanned() {
        LongSet regions = new LongOpenHashSet(new long[] {ChunkPos.pack(0, 0)});

        long[] tiles = ImportTiles.plan(regions, PLAYER_CHUNK, PLAYER_CHUNK, PLAYER_CHUNK - 1);

        assertArrayEquals(new long[] {ChunkPos.pack(0, 0)}, tiles);
    }
}
