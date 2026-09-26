package com.eminus.ingest;

import com.eminus.Eminus;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;
import com.eminus.cell.VoxelEntry;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.jspecify.annotations.Nullable;

public final class SectionConverter {
    public static final String DICTIONARY_NAME = "biome";
    public static final int DEFAULT_SKY_LIGHT = VoxelEntry.MAX_LIGHT;
    public static final int DEFAULT_BLOCK_LIGHT = 0;

    private static final String UNREGISTERED_BIOME = Eminus.MODID + ":unregistered";
    private static final int FALLBACK_BIOME_ID = 0;
    private static final int NIBBLE_BITS = 4;
    private static final int ROW_BYTES = DataLayer.SIZE / SectionPyramid.SECTION_SIDE;

    public static void convert(LevelChunkSection section, BiomeWindow window, DataLayer skyLight,
            DataLayer blockLight, StateTable states, Dictionary<String> biomes, SectionPyramid into) {
        into.reset();

        long[] level = into.level(DetailLevel.MIN);
        Reference2IntMap<BlockState> known = into.stateIds();

        for (int y = 0; y < SectionPyramid.SECTION_SIDE; y++) {
            for (int z = 0; z < SectionPyramid.SECTION_SIDE; z++) {
                for (int x = 0; x < SectionPyramid.SECTION_SIDE; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    int stateId = known.getInt(state);
                    if (stateId == SectionPyramid.ABSENT) {
                        stateId = states.idOf(state);
                        known.put(state, stateId);
                    }

                    int biomeId = biomeId(window.at(x, y, z), biomes, into);
                    int light = VoxelEntry.light(
                            skyLight == null ? DEFAULT_SKY_LIGHT : skyLight.get(x, y, z),
                            blockLight == null ? DEFAULT_BLOCK_LIGHT : blockLight.get(x, y, z));

                    level[SectionPyramid.indexAt(SectionPyramid.SECTION_SIDE, x, y, z)] =
                            VoxelEntry.pack(stateId, biomeId, light);
                }
            }
        }
    }

    public static boolean lightDiffersFromBlank(@Nullable DataLayer skyLight, @Nullable DataLayer blockLight) {
        return !filledWith(skyLight, DEFAULT_SKY_LIGHT) || !filledWith(blockLight, DEFAULT_BLOCK_LIGHT);
    }

    public static DataLayer repeatBottomRow(DataLayer layer) {
        if (layer.isDefinitelyHomogenous()) {
            return layer.copy();
        }

        byte[] source = layer.getData();
        byte[] repeated = new byte[DataLayer.SIZE];
        for (int y = 0; y < SectionPyramid.SECTION_SIDE; y++) {
            System.arraycopy(source, 0, repeated, y * ROW_BYTES, ROW_BYTES);
        }

        return new DataLayer(repeated);
    }

    private static boolean filledWith(@Nullable DataLayer layer, int value) {
        if (layer == null || layer.isDefinitelyFilledWith(value)) {
            return true;
        }

        if (layer.isDefinitelyHomogenous()) {
            return false;
        }

        byte packed = (byte) (value << NIBBLE_BITS | value);
        for (byte nibbles : layer.getData()) {
            if (nibbles != packed) {
                return false;
            }
        }

        return true;
    }

    private static int biomeId(@Nullable Holder<Biome> holder, Dictionary<String> biomes, SectionPyramid into) {
        if (holder == null) {
            return VoxelEntry.KEPT_BIOME;
        }

        Reference2IntMap<Holder<Biome>> known = into.biomeIds();
        int id = known.getInt(holder);
        if (id == SectionPyramid.ABSENT) {
            id = idOf(holder, biomes);
            known.put(holder, id);
        }

        return id;
    }

    private static int idOf(Holder<Biome> holder, Dictionary<String> biomes) {
        String key = holder.unwrapKey().map(resource -> resource.location().toString()).orElse(UNREGISTERED_BIOME);
        int known = biomes.id(key);
        if (known != Dictionary.MISSING) {
            return known;
        }

        int id = biomes.register(key);
        if (id > VoxelEntry.MAX_BIOME_ID) {
            Eminus.LOGGER.error("The biome dictionary passed {} entries at {}; the voxel entry cannot hold that id.",
                    VoxelEntry.MAX_BIOME_ID, key);
            return FALLBACK_BIOME_ID;
        }

        return id;
    }

    private SectionConverter() {
    }
}
