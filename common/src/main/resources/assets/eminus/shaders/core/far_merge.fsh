#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Merge {
    mat4 GameInverse;
    mat4 FarInverse;
    float MergedNear;
    float MergedFar;
    float HandDepth;
};

#include <eminus:far_depth.glsl>

uniform sampler2D GameDepth;
uniform sampler2D FarDepth;

layout(location = 0) out float mergedDepth;

float forward_ndc_z(float depth) {
#ifdef DEPTH_REVERSED
    return 1.0 - 2.0 * depth;
#else
    return depth * 2.0 - 1.0;
#endif
}

float view_distance(mat4 inverse, vec2 uv, float depth) {
    vec4 view = inverse * vec4(uv * 2.0 - 1.0, forward_ndc_z(depth), 1.0);
    return -view.z / view.w;
}

float merged_depth(float viewDistance) {
    float span = MergedFar - MergedNear;
    float ndcZ = (MergedFar + MergedNear) / span - 2.0 * MergedFar * MergedNear / (span * viewDistance);
    return ndcZ * 0.5 + 0.5;
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec2 uv = (vec2(texel) + 0.5) / vec2(textureSize(GameDepth, 0));
    float game = texelFetch(GameDepth, texel, 0).r;
    if (NEARER(game, FARTHEST)) {
        bool hand = forward_ndc_z(game) * 0.5 + 0.5 < HandDepth;
        mergedDepth = hand ? 0.0 : merged_depth(view_distance(GameInverse, uv, game));
        return;
    }

    float lod = texelFetch(FarDepth, texel, 0).r;
    mergedDepth = NEARER(lod, FARTHEST) ? merged_depth(view_distance(FarInverse, uv, lod)) : 1.0;
}
