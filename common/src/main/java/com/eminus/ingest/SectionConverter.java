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

public final class SectionConverter {
    public static final String DICTIONARY_NAME = "biome";
    public static final int DEFAULT_SKY_LIGHT = VoxelEntry.MAX_LIGHT;
    public static final int DEFAULT_BLOCK_LIGHT = 0;

    private static final String UNREGISTERED_BIOME = Eminus.MODID + ":unregistered";
    private static final int FALLBACK_BIOME_ID = 0;

    public static void convert(LevelChunkSection section, DataLayer skyLight, DataLayer blockLight,
            StateTable states, Dictionary<String> biomes, SectionPyramid into) {
        into.reset();
        sampleBiomes(section, biomes, into);

        long[] level = into.level(DetailLevel.MIN);
        int[] samples = into.biomeSamples();
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

                    int biomeId = samples[SectionPyramid.biomeIndexAt(
                            x >> LevelChunkSection.BIOME_CONTAINER_BITS,
                            y >> LevelChunkSection.BIOME_CONTAINER_BITS,
                            z >> LevelChunkSection.BIOME_CONTAINER_BITS)];
                    int light = VoxelEntry.light(
                            skyLight == null ? DEFAULT_SKY_LIGHT : skyLight.get(x, y, z),
                            blockLight == null ? DEFAULT_BLOCK_LIGHT : blockLight.get(x, y, z));

                    level[SectionPyramid.indexAt(SectionPyramid.SECTION_SIDE, x, y, z)] =
                            VoxelEntry.pack(stateId, biomeId, light);
                }
            }
        }
    }

    private static void sampleBiomes(LevelChunkSection section, Dictionary<String> biomes, SectionPyramid into) {
        int[] samples = into.biomeSamples();
        Reference2IntMap<Holder<Biome>> known = into.biomeIds();

        for (int quartY = 0; quartY < SectionPyramid.BIOME_SIDE; quartY++) {
            for (int quartZ = 0; quartZ < SectionPyramid.BIOME_SIDE; quartZ++) {
                for (int quartX = 0; quartX < SectionPyramid.BIOME_SIDE; quartX++) {
                    Holder<Biome> holder = section.getNoiseBiome(quartX, quartY, quartZ);
                    int id = known.getInt(holder);
                    if (id == SectionPyramid.ABSENT) {
                        id = idOf(holder, biomes);
                        known.put(holder, id);
                    }

                    samples[SectionPyramid.biomeIndexAt(quartX, quartY, quartZ)] = id;
                }
            }
        }
    }

    private static int idOf(Holder<Biome> holder, Dictionary<String> biomes) {
        String key = holder.unwrapKey().map(resource -> resource.identifier().toString()).orElse(UNREGISTERED_BIOME);
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
