package com.eminus.render.backend;

import com.mojang.blaze3d.GpuFormat;

import org.jspecify.annotations.Nullable;

public record BackendSupport(
        @Nullable BackendLimitation limitation,
        @Nullable GpuFormat depthFormat,
        DepthConvention depth) {

    public static final String ACCEPTED = "accepted";

    public static BackendSupport accepted(GpuFormat depthFormat, DepthConvention depth) {
        return new BackendSupport(null, depthFormat, depth);
    }

    public static BackendSupport refused(BackendLimitation limitation, DepthConvention depth) {
        return new BackendSupport(limitation, null, depth);
    }

    public boolean accepted() {
        return limitation == null;
    }

    public String reason() {
        return limitation == null ? ACCEPTED : limitation.reason();
    }
}
