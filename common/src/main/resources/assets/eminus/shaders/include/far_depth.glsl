#ifndef EMINUS_FAR_DEPTH_GLSL
#define EMINUS_FAR_DEPTH_GLSL

#ifdef DEPTH_REVERSED
#define NEARER(a, b) ((a) > (b))
#define FARTHER(depth, bias) ((depth) - (bias))
#define CLOSER(depth, bias) ((depth) + (bias))
#else
#define NEARER(a, b) ((a) < (b))
#define FARTHER(depth, bias) ((depth) + (bias))
#define CLOSER(depth, bias) ((depth) - (bias))
#endif

float far_ndc_z(float depth) {
#ifdef DEPTH_ZERO_TO_ONE
    return depth;
#else
    return depth * 2.0 - 1.0;
#endif
}

float far_window_z(float ndcZ) {
#ifdef DEPTH_ZERO_TO_ONE
    return ndcZ;
#else
    return ndcZ * 0.5 + 0.5;
#endif
}

vec4 far_ndc(vec2 uv, float depth) {
    return vec4(uv * 2.0 - 1.0, far_ndc_z(depth), 1.0);
}

vec3 far_unproject(mat4 inverse, vec2 uv, float depth) {
    vec4 eye = inverse * far_ndc(uv, depth);
    return eye.xyz / eye.w;
}

float far_reproject(mat4 toTarget, vec2 uv, float depth) {
    vec4 clip = toTarget * far_ndc(uv, depth);
    return far_window_z(clip.z / clip.w);
}

float far_mask_depth(mat4 gameToFar, vec2 uv, float game, float bias) {
    return clamp(far_reproject(gameToFar, uv, CLOSER(game, bias)), 0.0, 1.0);
}

#endif
