package com.eminus.gpu.pass;

import java.util.OptionalDouble;

import com.eminus.gpu.texture.Texture;

import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

public record PassSpec(
        String label,
        Texture color,
        @Nullable Vector4fc clearColor,
        @Nullable Texture depth,
        OptionalDouble clearDepth) {

    public static PassSpec of(String label, Texture color, @Nullable Vector4fc clearColor) {
        return new PassSpec(label, color, clearColor, null, OptionalDouble.empty());
    }

    public PassSpec withDepth(Texture depth, OptionalDouble clearDepth) {
        return new PassSpec(label, color, clearColor, depth, clearDepth);
    }
}
