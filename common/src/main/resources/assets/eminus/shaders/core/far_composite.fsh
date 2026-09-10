#version 330

layout(std140) uniform Composite {
    mat4 Reproject;
    float DepthBias;
};

uniform sampler2D FarColour;
uniform sampler2D FarDepth;

in vec2 screenUV;

out vec4 fragColor;

void main() {
    float depth = texture(FarDepth, screenUV).r;
    if (depth <= FARTHEST) {
        discard;
    }

#ifdef DEPTH_ZERO_TO_ONE
    float ndcZ = depth;
#else
    float ndcZ = depth * 2.0 - 1.0;
#endif

    vec4 reprojected = Reproject * vec4(screenUV * 2.0 - 1.0, ndcZ, 1.0);
    float gameZ = reprojected.z / reprojected.w;

#ifndef DEPTH_ZERO_TO_ONE
    gameZ = gameZ * 0.5 + 0.5;
#endif

    gl_FragDepth = clamp(gameZ - DepthBias, 0.0, 1.0);
    fragColor = texture(FarColour, screenUV);
}
