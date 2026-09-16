package com.eminus.render.backend;

public enum BackendLimitation {
    DRAW_INDIRECT("the device cannot draw from an indirect buffer"),
    FRAGMENT_DEPTH("the fragment stage cannot write depth"),
    DEPTH_STENCIL_TARGET("no depth-stencil target format is accepted"),
    ARENA_MEMORY("the memory allocation limit is below the arena");

    private final String reason;

    BackendLimitation(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
