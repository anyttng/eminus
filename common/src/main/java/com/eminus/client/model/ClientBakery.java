package com.eminus.client.model;

import com.eminus.cell.StateTable;
import com.eminus.client.frame.GameFrames;
import com.eminus.client.model.game.GameModels;
import com.eminus.mesh.MeshOpacity;
import com.eminus.model.BiomeColours;
import com.eminus.model.FluidBaker;
import com.eminus.model.ModelBaker;
import com.eminus.model.ModelBakery;
import com.eminus.model.SolidSprites;
import com.eminus.model.port.VariantDraw;

import net.minecraft.client.Minecraft;

public record ClientBakery(ModelBakery bakery, BiomeColours colours, boolean cutoutLeaves, VariantDraw variantDraw) {
    public static ClientBakery start(Minecraft client) {
        return start(client, GameFrames.cutoutLeaves());
    }

    public static ClientBakery start(Minecraft client, boolean cutoutLeaves) {
        GameModels game = GameModels.of(client, cutoutLeaves);
        SolidSprites sprites = new SolidSprites();
        BiomeColours colours = new BiomeColours(game.biomes());
        ModelBakery bakery = ModelBakery.start(new ModelBaker(game.blocks(), game.tints(),
                new FluidBaker(game.fluids(), sprites), sprites, colours));
        return new ClientBakery(bakery, colours, cutoutLeaves, game.blocks().variantDraw());
    }

    public MeshOpacity opacity(StateTable states) {
        return MeshOpacity.of(states, cutoutLeaves);
    }

    public void stop() {
        bakery.stop();
    }
}
