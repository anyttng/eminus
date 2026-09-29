#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Mask {
    mat4 GameToFar;
    float DepthBias;
};

#include <eminus:far_depth.glsl>

uniform sampler2D GameDepth;

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    float game = texelFetch(GameDepth, texel, 0).r;
    if (!NEARER(game, FARTHEST)) {
        discard;
    }

    float closer = CLOSER(game, DepthBias);
#ifdef DEPTH_ZERO_TO_ONE
    float ndcZ = closer;
#else
    float ndcZ = closer * 2.0 - 1.0;
#endif

    vec2 uv = (vec2(texel) + 0.5) / vec2(textureSize(GameDepth, 0));
    vec4 far = GameToFar * vec4(uv * 2.0 - 1.0, ndcZ, 1.0);
    float farZ = far.z / far.w;

#ifndef DEPTH_ZERO_TO_ONE
    farZ = farZ * 0.5 + 0.5;
#endif

    gl_FragDepth = clamp(farZ, 0.0, 1.0);
}
