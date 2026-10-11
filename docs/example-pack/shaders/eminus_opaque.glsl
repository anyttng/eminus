#version 330 compatibility

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

#include "/lib/terrain.glsl"

void eminus_emitFragment(EminusFragment fragment) {
    color = shadeTerrain(fragment.color, fragment.lmcoord);
}
