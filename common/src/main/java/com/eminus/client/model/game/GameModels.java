package com.eminus.client.model.game;

import java.util.List;

import com.eminus.model.port.BlockModels;
import com.eminus.model.port.BlockTints;
import com.eminus.model.port.FluidModels;
import com.eminus.model.port.TintBiome;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSpecialEffects;

import org.jspecify.annotations.Nullable;

public record GameModels(BlockModels blocks, BlockTints tints, FluidModels fluids, List<TintBiome> biomes) {
    private static @Nullable LoaderModels loader;

    public static void useLoader(LoaderModels chosen) {
        loader = chosen;
    }

    public static GameModels of(Minecraft client, boolean cutoutLeaves) {
        LoaderModels models = loader();
        BlockModels blocks = new GameBlockModels(client.getModelManager().getBlockModelShaper(), cutoutLeaves, models);
        return new GameModels(blocks, new GameTints(client.getBlockColors(), blocks), new GameFluids(models),
                biomes(client.level));
    }

    private static LoaderModels loader() {
        if (loader == null) {
            throw new IllegalStateException(
                    "LoaderModels is not set — the loader entrypoint must call GameModels.useLoader first.");
        }

        return loader;
    }

    private static List<TintBiome> biomes(@Nullable ClientLevel level) {
        if (level == null) {
            return List.of();
        }

        return level.registryAccess().lookupOrThrow(Registries.BIOME).listElements()
                .<TintBiome>map(biome -> new BakeLevel(biome.value(), biome.key().location().toString(),
                        biome.value().getSpecialEffects().getGrassColorModifier()
                                != BiomeSpecialEffects.GrassColorModifier.NONE))
                .toList();
    }
}
