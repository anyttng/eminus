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

    vec2 uv = (vec2(texel) + 0.5) / vec2(textureSize(GameDepth, 0));
    gl_FragDepth = far_mask_depth(GameToFar, uv, game, DepthBias);
}
