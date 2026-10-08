package com.eminus.client.mesh;

import com.eminus.client.model.ClientBakery;
import com.eminus.mesh.BakeryModels;
import com.eminus.mesh.BakeryTints;
import com.eminus.mesh.MeshListener;
import com.eminus.mesh.MeshModels;
import com.eminus.mesh.MeshService;
import com.eminus.model.ModelIndex;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import net.minecraft.client.Minecraft;

public final class MeshWiring {
    public static MeshService service(Minecraft client, DimensionRuntime runtime, EminusInstance instance,
            ClientBakery baking, MeshListener listener) {
        return new MeshService(instance.build(), runtime.cells(), runtime.coverage(), runtime.frame(),
                models(runtime, baking), new BakeryTints(baking.colours(), runtime.biomes()),
                client.options.biomeBlendRadius().get(), baking.opacity(runtime.states()), listener);
    }

    public static MeshModels models(DimensionRuntime runtime, ClientBakery baking) {
        return new BakeryModels(new ModelIndex(runtime.states(), baking.bakery()), baking.bakery());
    }

    private MeshWiring() {
    }
}
