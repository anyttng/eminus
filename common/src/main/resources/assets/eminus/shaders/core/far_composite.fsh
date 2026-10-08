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
    vec4 far = texture(FarColour, screenUV);
    if (!NEARER(depth, FARTHEST) || far.a == 0.0) {
        discard;
    }

    vec3 position = far_unproject(FarInverse, screenUV, depth);
    float fade = linear_fog_value(length(position.xz), FadeStart, FadeEnd);
    if (fade >= 1.0) {
        discard;
    }

    float gameZ = far_reproject(Reproject, screenUV, depth);
    float distance = length(position);
    float fog = distance <= FogReach
            ? linear_fog_value(distance, GameFogStart, GameFogEnd)
            : linear_fog_value(distance, FogStart, FogEnd);

    gl_FragDepth = clamp(FARTHER(gameZ, DepthBias), 0.0, 1.0);
    float coverage = far.a * (1.0 - fade);
    fragColor = vec4(mix(far.rgb, FogColour.rgb * far.a, fog * FogColour.a) * (1.0 - fade), coverage);
}
