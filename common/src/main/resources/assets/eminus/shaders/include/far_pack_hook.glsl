#ifndef EMINUS_FAR_PACK_HOOK_GLSL
#define EMINUS_FAR_PACK_HOOK_GLSL

struct EminusVertex {
    vec3 playerPos;
    int blockId;
    int face;
    bool blade;
    bool top;
    vec2 lmcoord;
    int level;
    bool shadow;
};

vec4 eminus_project(vec3 playerPos) {
    return EminusProjView * vec4(playerPos, 1.0);
}

vec4 eminus_vertexPosition(inout EminusVertex vertex);

#endif
