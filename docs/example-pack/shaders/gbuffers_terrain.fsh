#version 330 compatibility

uniform sampler2D gtexture;
uniform sampler2D lightmap;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

const float ALPHA_CUTOUT = 0.1;

void main() {
    color = texture(gtexture, texcoord) * glcolor;
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    color *= texture(lightmap, lmcoord);
}
