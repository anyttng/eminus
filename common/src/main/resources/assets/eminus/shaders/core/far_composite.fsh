#version 330

layout(std140) uniform Composite {
    mat4 Reproject;
    mat4 FarInverse;
    vec4 FogColour;
    float GameFogStart;
    float GameFogEnd;
    float FogReach;
    float FogStart;
    float FogEnd;
    float FadeStart;
    float FadeEnd;
    float DepthBias;
};

#moj_import <eminus:far_depth.glsl>

uniform sampler2D FarColour;
uniform sampler2D FarDepth;

in vec2 screenUV;

out vec4 fragColor;

float linear_fog_value(float vertexDistance, float start, float end) {
    if (vertexDistance <= start) {
        return 0.0;
    }
    if (vertexDistance >= end) {
        return 1.0;
    }

    return (vertexDistance - start) / (end - start);
}

void main() {
    float depth = texture(FarDepth, screenUV).r;
    if (!NEARER(depth, FARTHEST) || !NEARER(NEAREST, depth)) {
        discard;
    }

#ifdef DEPTH_ZERO_TO_ONE
    float ndcZ = depth;
#else
    float ndcZ = depth * 2.0 - 1.0;
#endif

    vec4 ndc = vec4(screenUV * 2.0 - 1.0, ndcZ, 1.0);
    vec4 eye = FarInverse * ndc;
    vec3 position = eye.xyz / eye.w;

    float fade = linear_fog_value(length(position.xz), FadeStart, FadeEnd);
    if (fade >= 1.0) {
        discard;
    }

    vec4 reprojected = Reproject * ndc;
    float gameZ = reprojected.z / reprojected.w;

#ifndef DEPTH_ZERO_TO_ONE
    gameZ = gameZ * 0.5 + 0.5;
#endif

    float distance = length(position);
    float fog = distance <= FogReach
            ? linear_fog_value(distance, GameFogStart, GameFogEnd)
            : linear_fog_value(distance, FogStart, FogEnd);
    vec4 far = texture(FarColour, screenUV);

    gl_FragDepth = clamp(FARTHER(gameZ, DepthBias), 0.0, 1.0);
    float coverage = far.a * (1.0 - fade);
    fragColor = vec4(mix(far.rgb, FogColour.rgb * far.a, fog * FogColour.a) * (1.0 - fade), coverage);
}
