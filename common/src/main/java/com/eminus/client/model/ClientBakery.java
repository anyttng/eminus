package com.eminus.client.model;

import java.util.HashMap;
import java.util.Map;

import com.eminus.cell.StateTable;
import com.eminus.mesh.MeshOpacity;
import com.eminus.model.BakeLevel;
import com.eminus.model.BiomeColours;
import com.eminus.model.FluidBaker;
import com.eminus.model.ModelBaker;
import com.eminus.model.ModelBakery;
import com.eminus.model.SolidSprites;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.registries.Registries;

public record ClientBakery(ModelBakery bakery, BiomeColours colours, boolean cutoutLeaves) {
    public static ClientBakery start(Minecraft client) {
        return start(client, client.options.cutoutLeaves().get());
    }

    public static ClientBakery start(Minecraft client, boolean cutoutLeaves) {
        ModelManager manager = client.getModelManager();
        SolidSprites sprites = new SolidSprites();
        BiomeColours colours = new BiomeColours(levels(client.level));
        ModelBakery bakery = ModelBakery.start(new ModelBaker(manager.getBlockStateModelSet(),
                client.getBlockColors(), new FluidBaker(manager.getFluidStateModelSet(), sprites), sprites, colours,
                cutoutLeaves));
        return new ClientBakery(bakery, colours, cutoutLeaves);
    }

    public MeshOpacity opacity(StateTable states) {
        return MeshOpacity.of(states, cutoutLeaves);
    }

    public void stop() {
        bakery.stop();
    }

    private static Map<String, BlockAndTintGetter> levels(ClientLevel level) {
        Map<String, BlockAndTintGetter> levels = new HashMap<>();
        if (level == null) {
            return levels;
        }

        level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().forEach(biome ->
                levels.put(biome.key().identifier().toString(), new BakeLevel(biome.value())));
        return levels;
    }
}
