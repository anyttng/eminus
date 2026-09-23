package com.eminus.ingest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;

import org.jspecify.annotations.Nullable;

public final class BiomeWindow implements BiomeManager.NoiseBiomeSource {
    public static final int QUARTS_PER_SECTION = QuartPos.fromSection(1);
    public static final int MARGIN = 1;
    public static final int SIDE = QUARTS_PER_SECTION + 2 * MARGIN;

    private final Holder<Biome>[] quarts;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final boolean uniform;
    private final BiomeManager manager;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private BiomeWindow(Holder<Biome>[] quarts, int originX, int originY, int originZ, long seed) {
        this.quarts = quarts;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.uniform = allSame(quarts);
        this.manager = new BiomeManager(this, seed);
    }

    @SuppressWarnings("unchecked")
    public static BiomeWindow capture(BiomeManager.NoiseBiomeSource source, int sectionX, int sectionY, int sectionZ,
            long seed) {
        int originX = QuartPos.fromSection(sectionX) - MARGIN;
        int originY = QuartPos.fromSection(sectionY) - MARGIN;
        int originZ = QuartPos.fromSection(sectionZ) - MARGIN;
        Holder<Biome>[] quarts = (Holder<Biome>[]) new Holder<?>[SIDE * SIDE * SIDE];

        for (int y = 0; y < SIDE; y++) {
            for (int z = 0; z < SIDE; z++) {
                for (int x = 0; x < SIDE; x++) {
                    quarts[index(x, y, z)] = source.getNoiseBiome(originX + x, originY + y, originZ + z);
                }
            }
        }

        return new BiomeWindow(quarts, originX, originY, originZ, seed);
    }

    public boolean uniform() {
        return uniform;
    }

    public @Nullable Holder<Biome> at(int x, int y, int z) {
        if (uniform) {
            return quarts[0];
        }

        return manager.getBiome(pos.set(blockOrigin(originX) + x, blockOrigin(originY) + y, blockOrigin(originZ) + z));
    }

    @Override
    public @Nullable Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        return quarts[index(quartX - originX, quartY - originY, quartZ - originZ)];
    }

    private static int blockOrigin(int windowOrigin) {
        return QuartPos.toBlock(windowOrigin + MARGIN);
    }

    private static int index(int x, int y, int z) {
        return (y * SIDE + z) * SIDE + x;
    }

    private static boolean allSame(Holder<Biome>[] quarts) {
        for (Holder<Biome> quart : quarts) {
            if (quart != quarts[0]) {
                return false;
            }
        }

        return true;
    }
}
