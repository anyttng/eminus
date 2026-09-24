package com.eminus.gpu.pipeline;

import com.eminus.gpu.Format;

import org.jspecify.annotations.Nullable;

public record Binding(String name, Kind kind, @Nullable Format format) {
    public static Binding uniform(String name) {
        return new Binding(name, Kind.UNIFORM, null);
    }

    public static Binding texel(String name, Format format) {
        return new Binding(name, Kind.TEXEL, format);
    }

    public static Binding sampled(String name) {
        return new Binding(name, Kind.SAMPLED, null);
    }

    public static Binding storage(String name) {
        return new Binding(name, Kind.STORAGE, null);
    }

    public enum Kind {
        UNIFORM,
        TEXEL,
        SAMPLED,
        STORAGE
    }
}
