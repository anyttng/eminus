package com.eminus.client.model.game;

import java.util.List;

import com.eminus.model.port.BlockModels;
import com.eminus.model.port.BlockTints;
import com.eminus.model.port.FluidModels;
import com.eminus.model.port.TintBiome;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSpecialEffects;

import org.jspecify.annotations.Nullable;

public record GameModels(BlockModels blocks, BlockTints tints, FluidModels fluids, List<TintBiome> biomes) {
    public static GameModels of(Minecraft client, boolean cutoutLeaves) {
        ModelManager manager = client.getModelManager();
        return new GameModels(new GameBlockModels(manager.getBlockStateModelSet(), cutoutLeaves),
                new GameTints(client.getBlockColors()), new GameFluids(manager.getFluidStateModelSet()),
                biomes(client.level));
    }

    private static List<TintBiome> biomes(@Nullable ClientLevel level) {
        if (level == null) {
            return List.of();
        }

        return level.registryAccess().lookupOrThrow(Registries.BIOME).listElements()
                .<TintBiome>map(biome -> new BakeLevel(biome.value(), biome.key().identifier().toString(),
                        biome.value().getSpecialEffects().grassColorModifier()
                                != BiomeSpecialEffects.GrassColorModifier.NONE))
                .toList();
    }
}
