package com.eminus.client.model;

import com.eminus.cell.StateTable;
import com.eminus.client.model.game.GameModels;
import com.eminus.mesh.MeshOpacity;
import com.eminus.model.BiomeColors;
import com.eminus.model.FluidBaker;
import com.eminus.model.ModelBaker;
import com.eminus.model.ModelBakery;
import com.eminus.model.PackBlocks;
import com.eminus.model.SpriteTexels;
import com.eminus.model.port.VariantDraw;
import com.eminus.settings.SettingsService;

import net.minecraft.client.Minecraft;

public record ClientBakery(ModelBakery bakery, BiomeColors colors, boolean seeThroughLeaves, boolean drawsBlades,
        VariantDraw variantDraw) {
    public static ClientBakery start(Minecraft client) {
        return start(client, PackBlocks.NONE);
    }

    public static ClientBakery start(Minecraft client, PackBlocks packBlocks) {
        return start(client, SettingsService.get().settings().lodQuality().seeThroughLeaves(), packBlocks);
    }

    public static ClientBakery start(Minecraft client, boolean seeThroughLeaves) {
        return start(client, seeThroughLeaves, PackBlocks.NONE);
    }

    public static ClientBakery start(Minecraft client, boolean seeThroughLeaves, PackBlocks packBlocks) {
        GameModels game = GameModels.of(client, seeThroughLeaves);
        SpriteTexels sprites = new SpriteTexels();
        BiomeColors colors = new BiomeColors(game.biomes());
        boolean drawsBlades = SettingsService.get().settings().lodQuality().drawsBlades();
        ModelBakery bakery = ModelBakery.start(new ModelBaker(game.blocks(), game.tints(),
                new FluidBaker(game.fluids(), sprites), sprites, colors, drawsBlades), packBlocks);
        return new ClientBakery(bakery, colors, seeThroughLeaves, drawsBlades, game.blocks().variantDraw());
    }

    public MeshOpacity opacity(StateTable states) {
        return MeshOpacity.of(states, seeThroughLeaves);
    }

    public void stop() {
        bakery.stop();
    }
}
