#ifndef EMINUS_FAR_LIGHTMAP_GLSL
#define EMINUS_FAR_LIGHTMAP_GLSL

const float FAR_LIGHTMAP_SCALE = 256.0;
const float FAR_LIGHTMAP_HALF_TEXEL = 0.5 / 16.0;
const float FAR_LIGHTMAP_LAST_TEXEL = 15.5 / 16.0;

#ifdef LIGHTMAP_HALF_TEXEL
const float FAR_LIGHTMAP_SHIFT = FAR_LIGHTMAP_HALF_TEXEL;
#else
const float FAR_LIGHTMAP_SHIFT = 0.0;
#endif

vec4 far_lightmap(sampler2D lightmap, ivec2 uv) {
    return texture(lightmap, clamp(vec2(uv) / FAR_LIGHTMAP_SCALE + FAR_LIGHTMAP_SHIFT,
            vec2(FAR_LIGHTMAP_HALF_TEXEL), vec2(FAR_LIGHTMAP_LAST_TEXEL)));
}

#endif
