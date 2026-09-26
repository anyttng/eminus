#version 330
#extension GL_ARB_separate_shader_objects : require

#include <eminus:far_depth.glsl>

uniform sampler2D GameDepth;

void main() {
    if (!NEARER(texelFetch(GameDepth, ivec2(gl_FragCoord.xy), 0).r, FARTHEST)) {
        discard;
    }

    gl_FragDepth = NEAREST;
}
