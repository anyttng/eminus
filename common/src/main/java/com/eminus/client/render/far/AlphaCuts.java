package com.eminus.client.render.far;

public record AlphaCuts(AlphaCut opaque, AlphaCut translucent, AlphaCut unmipped) {
    public static final AlphaCuts OWN = own(OpaquePass.ALPHA_CUTOUT);

    public static AlphaCuts own(float opaqueCutout) {
        return new AlphaCuts(AlphaCut.atLeast(opaqueCutout), AlphaCut.atLeast(TranslucentPass.ALPHA_CUTOUT),
                AlphaCut.atLeast(FarQuads.UNMIPPED_ALPHA_CUTOUT));
    }

    public static AlphaCuts pack(AlphaCut opaque, AlphaCut translucent) {
        return new AlphaCuts(opaque, translucent, opaque);
    }
}
