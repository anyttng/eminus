#version 330 compatibility

uniform sampler2D colortex0;
uniform vec3 fogColor;
uniform float far;
uniform int eminusRenderDistance;

in vec2 texcoord;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

const float SKY = 0.0;
const float FOG_START = 0.5;

void main() {
    color = texture(colortex0, texcoord);

    vec4 position = eminus_viewPosition(texcoord, false);
    if (position.w == SKY) {
        return;
    }

    float fogEnd = eminusRenderDistance > 0 ? float(eminusRenderDistance) : far;
    color.rgb = mix(color.rgb, fogColor, smoothstep(fogEnd * FOG_START, fogEnd, length(position.xyz)));
}
