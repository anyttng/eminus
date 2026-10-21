package com.eminus.compat.sodium;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.eminus.Eminus;
import com.google.common.base.Splitter;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import net.minecraft.resources.ResourceLocation;

public final class SodiumCutout {
    private static final ResourceLocation MATERIAL_SHADER =
            ResourceLocation.fromNamespaceAndPath("sodium", "include/chunk_material.glsl");
    private static final Pattern CUTOFF_TABLE = Pattern.compile("ALPHA_CUTOFF\\s*=\\s*float\\[\\d+]\\(([^)]*)\\)");
    private static final Splitter ENTRIES = Splitter.on(',').trimResults();

    @SuppressWarnings("EnumOrdinal")
    public static float read(float fallback) {
        int half = AlphaCutoffParameter.HALF.ordinal();
        String source;
        try {
            source = ShaderLoader.getShaderSource(MATERIAL_SHADER);
        } catch (RuntimeException unreadable) {
            Eminus.LOGGER.warn("[eminus/compat] sodium_cutout value={} text={}", fallback, unreadable.toString());
            return fallback;
        }

        Float cutout = parse(source, half);
        if (cutout == null) {
            Eminus.LOGGER.warn("[eminus/compat] sodium_cutout value={} text=no ALPHA_CUTOFF entry {} in {}",
                    fallback, half, MATERIAL_SHADER);
            return fallback;
        }

        return cutout;
    }

    static @Nullable Float parse(String source, int index) {
        Matcher table = CUTOFF_TABLE.matcher(source);
        if (!table.find()) {
            return null;
        }

        List<String> entries = ENTRIES.splitToList(table.group(1));
        if (index >= entries.size()) {
            return null;
        }

        try {
            return Float.parseFloat(entries.get(index));
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    private SodiumCutout() {
    }
}
