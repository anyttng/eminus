package com.eminus.client.render.far;

import java.util.Objects;

import com.eminus.Eminus;
import com.eminus.client.handoff.NearMaskPass;
import com.eminus.client.handoff.NearSectionTable;
import com.eminus.client.model.ClientBakery;
import com.eminus.client.model.ModelPublisher;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.client.render.backend.BackendCheck;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.texture.Texture;
import com.eminus.model.PackBlocks;
import com.eminus.render.backend.BackendSupport;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;
import com.eminus.settings.Settings;

import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;

public record FarStart(@Nullable FarRenderer renderer, @Nullable String refusal) {

    public static FarStart start(Minecraft client, Gpu gpu, EminusInstance instance,
            DimensionRuntime runtime, int levelHeight, Settings settings, long replacedArenaBytes,
            PackBlocks packBlocks) {
        gpu.assertRenderThread();

        Texture main = gpu.mainColor();
        FarSizing sizing = FarSizing.of(client, gpu, gpu.capabilities().freeBytes(), replacedArenaBytes,
                runtime.lowestStoredLevel(), settings, levelHeight);
        BackendSupport support = BackendCheck.run(gpu, sizing.arenaBytes());
        GeometryArena arena = GeometryArena.create(gpu, support, sizing.arenaBytes(), sizing.sparse());
        if (arena == null) {
            gpu.close();
            return refused(support.reason());
        }

        ClientBakery baking = ClientBakery.start(client, packBlocks);
        NearMaskPass mask = NearMaskPass.create(gpu, FarTarget.COLOR_FORMAT, support.depth(),
                CompositePass.DEPTH_BIAS);
        OpaquePass opaque = OpaquePass.create(gpu, support.depth(), baking.variantDraw());
        TranslucentPass translucent = TranslucentPass.create(gpu, support.depth(), baking.variantDraw());
        CompositePass composite = CompositePass.create(gpu, support.depth());
        FarOverlays overlays = FarOverlays.create(gpu, support.depth());
        String refusal = FarPrograms.refusal(mask, opaque, translucent, composite, overlays);
        if (refusal != null) {
            return refused(refusal, gpu, arena, baking, mask, composite, overlays);
        }

        FarRenderer renderer = new FarRenderer(gpu, support.depth(), runtime, baking,
                ModelPublisher.start(gpu, baking.bakery(), FarRenderer.atlasFirstMip(settings.lodQuality())), arena,
                FarTarget.create(gpu, Objects.requireNonNull(support.depthFormat(), "an accepted backend names one"),
                        main.width(), main.height()), FarFrame.create(gpu),
                mask, NearSectionTable.create(gpu), opaque, translucent, composite, overlays,
                IndirectCommands.create(gpu, FarRenderer.START_COMMANDS), levelHeight, sizing.nodeCapacity());
        FarRenderer.wire(renderer, client, runtime, instance, baking);
        return started(renderer);
    }

    static FarStart started(FarRenderer renderer) {
        return new FarStart(renderer, null);
    }

    static FarStart refused(String refusal) {
        return new FarStart(null, refusal);
    }

    private static FarStart refused(String refusal, Gpu gpu, GeometryArena arena, ClientBakery baking,
            NearMaskPass mask, CompositePass composite, FarOverlays overlays) {
        Eminus.LOGGER.warn("Renderer disabled: {}", refusal);
        baking.stop();
        overlays.close();
        composite.close();
        mask.close();
        arena.close();
        gpu.close();
        return refused(refusal);
    }
}
