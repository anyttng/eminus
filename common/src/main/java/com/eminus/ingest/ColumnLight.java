package com.eminus.ingest;

import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

import org.jspecify.annotations.Nullable;

public final class ColumnLight {
    @FunctionalInterface
    public interface Layers {
        @Nullable DataLayer layer(LightLayer layer, int sectionY);
    }

    public static @Nullable DataLayer sky(Layers layers, boolean hasSky, int sectionY, int maxLightSection) {
        DataLayer own = layers.layer(LightLayer.SKY, sectionY);
        if (own != null || !hasSky) {
            return own;
        }

        for (int above = sectionY + 1; above < maxLightSection; above++) {
            DataLayer layer = layers.layer(LightLayer.SKY, above);
            if (layer != null) {
                return SectionConverter.repeatBottomRow(layer);
            }
        }

        return null;
    }

    public static DataLayer openSky(boolean hasSky) {
        return hasSky ? new DataLayer(SectionConverter.DEFAULT_SKY_LIGHT) : new DataLayer();
    }

    private ColumnLight() {
    }
}
