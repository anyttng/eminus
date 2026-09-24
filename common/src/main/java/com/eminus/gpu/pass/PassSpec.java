package com.eminus.gpu.pass;

import java.util.OptionalDouble;

import com.eminus.gpu.texture.Texture;

import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

public record PassSpec(
        String label,
        Texture colour,
        @Nullable Vector4fc clearColour,
        @Nullable Texture depth,
        OptionalDouble clearDepth) {

    public static PassSpec of(String label, Texture colour, @Nullable Vector4fc clearColour) {
        return new PassSpec(label, colour, clearColour, null, OptionalDouble.empty());
    }

    public PassSpec withDepth(Texture depth, OptionalDouble clearDepth) {
        return new PassSpec(label, colour, clearColour, depth, clearDepth);
    }
}
