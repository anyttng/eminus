#ifndef EMINUS_FAR_SURFACE_GLSL
#define EMINUS_FAR_SURFACE_GLSL

const float EMINUS_MIN_AXIS_LENGTH = 1.0e-6;
const int EMINUS_RGSS_TAPS = 4;
const vec2 EMINUS_RGSS_OFFSETS[EMINUS_RGSS_TAPS] = vec2[](
        vec2(0.125, 0.375),
        vec2(-0.125, -0.375),
        vec2(0.375, -0.125),
        vec2(-0.375, 0.125));

// The game's sampleNearest and sampleRGSS (texture_sampling.glsl), kept in step by hand.
vec2 eminus_nearest_uv(vec2 uv, vec2 texel, vec2 texelScreenSize) {
    vec2 coords = uv / texel;
    vec2 center = round(coords) - 0.5;
    vec2 offset = clamp((coords - center - 0.5) * texel / texelScreenSize + 0.5, 0.0, 1.0);
    return (center + offset) * texel;
}

// One level, held half of its own texel inside the cell: the game pads a sprite with its edge texels.
vec4 eminus_level(sampler2D source, vec2 uv, float level, vec2 texel, vec2 cellLow, vec2 cell) {
    vec2 margin = min(0.5 * texel * exp2(level), 0.5 * cell);
    return textureLod(source, clamp(uv, cellLow + margin, cellLow + cell - margin), level);
}

vec4 eminus_mipped(sampler2D source, vec2 uv, float level, vec2 texel, vec2 cellLow, vec2 cell) {
    float low = floor(max(level, 0.0));
    vec4 color = eminus_level(source, uv, low, texel, cellLow, cell);
    float blend = level - low;
    if (blend <= 0.0) {
        return color;
    }
    return mix(color, eminus_level(source, uv, low + 1.0, texel, cellLow, cell), blend);
}

vec4 eminus_sample(sampler2D source, vec2 uv, vec2 du, vec2 dv, vec2 texel, vec2 cellLow, vec2 cell) {
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float duLength = length(du);
    float dvLength = length(dv);
    float minTexel = min(texel.x, texel.y);
    float level = log2(max(max(duLength, dvLength), EMINUS_MIN_AXIS_LENGTH) / minTexel);
    vec4 nearest = eminus_mipped(source, eminus_nearest_uv(uv, texel, texelScreenSize), level, texel, cellLow,
            cell);
    if (EminusRgss == 0) {
        return nearest;
    }

    float effective = sqrt(min(duLength, dvLength) * max(duLength, dvLength));
    float rgssLevel = max(0.0, log2(max(effective, EMINUS_MIN_AXIS_LENGTH) / minTexel));
    vec4 rgss = vec4(0.0);
    for (int tap = 0; tap < EMINUS_RGSS_TAPS; tap++) {
        rgss += eminus_mipped(source, uv + EMINUS_RGSS_OFFSETS[tap] * texel, rgssLevel, texel, cellLow, cell);
    }
    rgss /= float(EMINUS_RGSS_TAPS);
    float blend = smoothstep(minTexel, 2.0 * minTexel, max(texelScreenSize.x, texelScreenSize.y));
    return mix(nearest, rgss, blend);
}

vec4 eminus_surface(ivec2 atlasCell, vec2 faceUV, vec3 tint) {
    vec2 cell = vec2(1.0) / float(EminusAtlasCells);
    vec2 texel = cell / float(EminusAtlasCellSide);
    vec2 cellLow = vec2(atlasCell) * cell;

    vec2 gradX = dFdx(faceUV);
    vec2 gradY = dFdy(faceUV);
    bool alongX = dot(gradX, gradX) >= dot(gradY, gradY);
    vec2 longAxis = alongX ? gradX : gradY;
    vec2 shortAxis = alongX ? gradY : gradX;

    int samples = 1;
    if (EminusAnisotropy > 1) {
        float ratio = length(longAxis) / max(length(shortAxis), EMINUS_MIN_AXIS_LENGTH);
        samples = int(clamp(ceil(ratio), 1.0, float(min(EminusAnisotropy, MAX_SAMPLES))));
    }
    vec2 stride = longAxis / float(samples);
    vec2 gradLong = stride * cell;
    vec2 gradShort = shortAxis * cell;

    vec2 point = faceUV - 0.5 * longAxis + 0.5 * stride;
    vec4 color = vec4(0.0);
    float mask = 0.0;
    for (int index = 0; index < samples; index++) {
        vec2 uv = cellLow + fract(point) * cell;
        color += eminus_sample(EminusAtlas, uv, gradLong, gradShort, texel, cellLow, cell);
        mask += eminus_sample(EminusTintMask, uv, gradLong, gradShort, texel, cellLow, cell).r;
        point += stride;
    }

    color /= float(samples);
    color.rgb *= mix(vec3(1.0), tint, mask / float(samples));
    return color;
}

#endif
