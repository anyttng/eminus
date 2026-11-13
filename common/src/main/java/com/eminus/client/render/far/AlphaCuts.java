package com.eminus.client.render.far;

public record AlphaCuts(AlphaCut opaque, AlphaCut translucent) {
    public static final AlphaCuts OWN = new AlphaCuts(AlphaCut.atLeast(OpaquePass.ALPHA_CUTOUT),
            AlphaCut.atLeast(TranslucentPass.ALPHA_CUTOUT));
}
