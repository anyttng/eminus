#ifndef EMINUS_FAR_SURFACE_GLSL
#define EMINUS_FAR_SURFACE_GLSL

const float MIN_AXIS_LENGTH = 1.0e-6;

vec4 far_surface(ivec2 atlasCell, vec2 faceUV, vec3 tint) {
    vec2 cell = vec2(1.0) / float(AtlasCells);
    float margin = 0.5 / float(FACE_SIDE);

    vec2 gradX = dFdx(faceUV);
    vec2 gradY = dFdy(faceUV);
    bool alongX = dot(gradX, gradX) >= dot(gradY, gradY);
    vec2 longAxis = alongX ? gradX : gradY;
    vec2 shortAxis = alongX ? gradY : gradX;

    float ratio = length(longAxis) / max(length(shortAxis), MIN_AXIS_LENGTH);
    int samples = int(clamp(ceil(ratio), 1.0, float(MAX_SAMPLES)));
    vec2 stride = longAxis / float(samples);
    vec2 gradLong = stride * cell;
    vec2 gradShort = shortAxis * cell;

    vec2 point = faceUV - 0.5 * longAxis + 0.5 * stride;
    vec4 colour = vec4(0.0);
    float mask = 0.0;
    for (int index = 0; index < samples; index++) {
        vec2 within = clamp(fract(point), margin, 1.0 - margin);
        vec2 uv = (vec2(atlasCell) + within) * cell;
        colour += textureGrad(Atlas, uv, gradLong, gradShort);
        mask += textureGrad(TintMask, uv, gradLong, gradShort).r;
        point += stride;
    }

    colour /= float(samples);
    colour.rgb *= mix(vec3(1.0), tint, mask / float(samples));
    return colour;
}

#endif
