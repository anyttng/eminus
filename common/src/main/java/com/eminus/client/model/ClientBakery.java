package com.eminus.client.model;

import com.eminus.cell.StateTable;
import com.eminus.client.frame.GameFrames;
import com.eminus.client.model.game.GameModels;
import com.eminus.client.model.game.SpriteMips;
import com.eminus.mesh.MeshOpacity;
import com.eminus.model.BiomeColours;
import com.eminus.model.FluidBaker;
import com.eminus.model.ModelBaker;
import com.eminus.model.ModelBakery;
import com.eminus.model.PackBlocks;
import com.eminus.model.SpriteTexels;
import com.eminus.model.port.VariantDraw;
import com.eminus.settings.SettingsService;

import net.minecraft.client.Minecraft;

public record ClientBakery(ModelBakery bakery, BiomeColours colours, boolean cutoutLeaves, boolean drawsBlades,
        VariantDraw variantDraw) {
    public static ClientBakery start(Minecraft client) {
        return start(client, PackBlocks.NONE);
    }

    public static ClientBakery start(Minecraft client, PackBlocks packBlocks) {
        return start(client, cutoutLeaves(client), packBlocks);
    }

    public static ClientBakery start(Minecraft client, boolean cutoutLeaves) {
        return start(client, cutoutLeaves, PackBlocks.NONE);
    }

    public static boolean cutoutLeaves(Minecraft client) {
        return SettingsService.get().settings().lodQuality().cutoutLeaves(GameFrames.cutoutLeaves(client));
    }

    public static ClientBakery start(Minecraft client, boolean cutoutLeaves, PackBlocks packBlocks) {
        GameModels game = GameModels.of(client, cutoutLeaves);
        SpriteTexels sprites = new SpriteTexels();
        BiomeColours colours = new BiomeColours(game.biomes());
        boolean drawsBlades = SettingsService.get().settings().lodQuality().drawsBlades();
        ModelBakery bakery = ModelBakery.start(new ModelBaker(game.blocks(), game.tints(),
                new FluidBaker(game.fluids(), sprites), sprites, colours, drawsBlades), packBlocks, SpriteMips::blend);
        return new ClientBakery(bakery, colours, cutoutLeaves, drawsBlades, game.blocks().variantDraw());
    }

    public MeshOpacity opacity(StateTable states) {
        return MeshOpacity.of(states, cutoutLeaves);
    }

    public void stop() {
        bakery.stop();
    }
}
