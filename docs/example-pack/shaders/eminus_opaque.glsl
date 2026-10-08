#version 330 compatibility

uniform sampler2D lightmap;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

void eminus_emitFragment(EminusFragment fragment) {
    color = fragment.color * texture(lightmap, fragment.lmcoord);
}
