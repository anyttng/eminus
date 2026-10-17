#ifndef EMINUS_FAR_PACK_INPUTS_GLSL
#define EMINUS_FAR_PACK_INPUTS_GLSL

const float EMINUS_LIGHTMAP_SCALE = 1.0 / 256.0;
const float EMINUS_LIGHTMAP_OFFSET = 1.0 / 32.0;

// The game's lightmap texture matrix, which takes gl_MultiTexCoord1's 0..240 to the lightmap's texel centers.
const mat4 eminus_textureMatrix[2] = mat4[2](mat4(1.0), mat4(
        EMINUS_LIGHTMAP_SCALE, 0.0, 0.0, 0.0,
        0.0, EMINUS_LIGHTMAP_SCALE, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        EMINUS_LIGHTMAP_OFFSET, EMINUS_LIGHTMAP_OFFSET, 0.0, 1.0));

#endif
