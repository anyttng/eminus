#version 330 compatibility

uniform sampler2D gtexture;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

const float ALPHA_CUTOUT = 0.1;

#include "/lib/terrain.glsl"

void main() {
    vec4 albedo = texture(gtexture, texcoord) * glcolor;
    if (albedo.a < ALPHA_CUTOUT) {
        discard;
    }
    color = shadeTerrain(albedo, lmcoord);
}
