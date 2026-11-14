package com.eminus.client.cell;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;
import com.eminus.ingest.IngestColumn;
import com.eminus.ingest.LiveColumn;
import com.eminus.ingest.PyramidDownsampler;
import com.eminus.ingest.SectionConverter;
import com.eminus.ingest.SectionPyramid;
import com.eminus.ingest.saved.SavedChunk;
import com.eminus.ingest.saved.SavedColumn;
import com.eminus.ingest.saved.ServerSave;
import com.eminus.mixin.BiomeManagerAccessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jspecify.annotations.Nullable;

public final class ImportCompareReading {
    public static final int CHUNK_X = 0;
    public static final int CHUNK_Z = 1;
    public static final int STATUS = 2;
    public static final int LIVE_SECTIONS = 3;
    public static final int SAVED_SECTIONS = 4;
    public static final int MISMATCHED_SECTIONS = 5;
    public static final int FIRST_SECTION_Y = 6;
    public static final int FIRST_LEVEL = 7;
    public static final int FIRST_VOXEL = 8;
    public static final int FIRST_LIVE_ENTRY = 9;
    public static final int FIRST_SAVED_ENTRY = 10;
    public static final int WIDTH = 11;

    public static final long COMPARED = 0L;
    public static final long LIVE_NOT_READY = 1L;
    public static final long SAVED_REFUSED = 2L;
    public static final long SUBMITTED_DIFFERS = -1L;

    public static @Nullable List<long[]> compare(int minChunkX, int minChunkZ, int sideChunks,
            Map<Long, CompoundTag> savedTags) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        IntegratedServer server = minecraft.getSingleplayerServer();
        ServerLevel serverLevel = level == null || server == null ? null : server.getLevel(level.dimension());
        if (level == null || serverLevel == null) {
            return null;
        }

        ServerSave save = new ServerSave(serverLevel);
        Long2ObjectMap<SavedChunk> saved = new Long2ObjectOpenHashMap<>();
        savedTags.forEach((packed, tag) -> {
            SavedChunk parsed = save.parse(tag);
            if (parsed != null) {
                saved.put(packed.longValue(), parsed);
            }
        });

        long seed = ((BiomeManagerAccessor) level.getBiomeManager()).eminus$biomeZoomSeed();
        Scratch scratch = new Scratch();
        List<long[]> rows = new ArrayList<>(sideChunks * sideChunks);
        for (int dz = 0; dz < sideChunks; dz++) {
            for (int dx = 0; dx < sideChunks; dx++) {
                rows.add(column(level, saved, save.hasSky(), seed, minChunkX + dx, minChunkZ + dz, scratch));
            }
        }

        return rows;
    }

    private static long[] column(ClientLevel level, Long2ObjectMap<SavedChunk> saved, boolean hasSky, long seed,
            int chunkX, int chunkZ, Scratch scratch) {
        long[] row = new long[WIDTH];
        row[CHUNK_X] = chunkX;
        row[CHUNK_Z] = chunkZ;
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        SavedChunk[] around = new SavedChunk[SavedColumn.NEIGHBOR_SIDE * SavedColumn.NEIGHBOR_SIDE];
        boolean neighborsHeld = chunk != null;
        for (int dz = -SavedColumn.NEIGHBOR_REACH; dz <= SavedColumn.NEIGHBOR_REACH; dz++) {
            for (int dx = -SavedColumn.NEIGHBOR_REACH; dx <= SavedColumn.NEIGHBOR_REACH; dx++) {
                around[SavedColumn.neighborIndex(dx, dz)] = saved.get(ChunkPos.pack(chunkX + dx, chunkZ + dz));
                neighborsHeld &= level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz) != null;
            }
        }

        LiveColumn live = chunk == null ? null : new LiveColumn(chunk);
        if (live == null || !neighborsHeld || !live.lightOn() || !live.hasLightData()) {
            row[STATUS] = LIVE_NOT_READY;
            return row;
        }

        SavedChunk center = around[SavedColumn.neighborIndex(0, 0)];
        if (center == null || SavedColumn.judge(around) != SavedColumn.Verdict.ACCEPTED) {
            row[STATUS] = SAVED_REFUSED;
            return row;
        }

        compare(live, new SavedColumn(center, around, hasSky, seed), scratch, row);
        return row;
    }

    private static void compare(IngestColumn live, IngestColumn saved, Scratch scratch, long[] row) {
        row[STATUS] = COMPARED;
        for (int index = 0; index < live.sectionCount(); index++) {
            boolean liveSubmits = live.submits(index);
            boolean savedSubmits = saved.submits(index);
            row[LIVE_SECTIONS] += liveSubmits ? 1 : 0;
            row[SAVED_SECTIONS] += savedSubmits ? 1 : 0;
            if (liveSubmits != savedSubmits) {
                mismatch(row, live.sectionY(index), SUBMITTED_DIFFERS, 0, liveSubmits ? 1 : 0, savedSubmits ? 1 : 0);
            } else if (liveSubmits) {
                scratch.build(live, index, scratch.live);
                scratch.build(saved, index, scratch.saved);
                compareLevels(live.sectionY(index), scratch, row);
            }
        }
    }

    private static void compareLevels(int sectionY, Scratch scratch, long[] row) {
        for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
            int side = SectionPyramid.sideOf(level);
            long[] live = scratch.live.level(level);
            long[] saved = scratch.saved.level(level);
            for (int voxel = 0; voxel < side * side * side; voxel++) {
                if (live[voxel] != saved[voxel]) {
                    mismatch(row, sectionY, level, voxel, live[voxel], saved[voxel]);
                    return;
                }
            }
        }
    }

    private static void mismatch(long[] row, int sectionY, long level, int voxel, long liveEntry, long savedEntry) {
        if (row[MISMATCHED_SECTIONS]++ == 0) {
            row[FIRST_SECTION_Y] = sectionY;
            row[FIRST_LEVEL] = level;
            row[FIRST_VOXEL] = voxel;
            row[FIRST_LIVE_ENTRY] = liveEntry;
            row[FIRST_SAVED_ENTRY] = savedEntry;
        }
    }

    private static final class Scratch {
        private final StateTable states = new StateTable(new Dictionary<>((id, value) -> { }));
        private final Dictionary<String> biomes = new Dictionary<>((id, value) -> { });
        private final SectionPyramid live = new SectionPyramid();
        private final SectionPyramid saved = new SectionPyramid();

        private void build(IngestColumn column, int index, SectionPyramid into) {
            SectionConverter.convert(column.section(index), column.biomeWindow(index), column.skyLight(index),
                    column.blockLight(index), states, biomes, into);
            PyramidDownsampler.build(into, states, states);
        }
    }

    private ImportCompareReading() {
    }
}
