#version 330 compatibility

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform sampler2D eminusDepthTex0;
uniform mat4 gbufferProjectionInverse;
uniform mat4 eminusProjectionInverse;
uniform vec3 fogColor;
uniform float far;
uniform int eminusRenderDistance;

in vec2 texcoord;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

const float SKY_DEPTH = 1.0;
const float FOG_START = 0.5;

float viewDistance(float depth, mat4 inverseProjection) {
    vec4 view = inverseProjection * vec4(vec3(texcoord, depth) * 2.0 - 1.0, 1.0);
    return length(view.xyz / view.w);
}

void main() {
    color = texture(colortex0, texcoord);

    float nearDepth = texture(depthtex0, texcoord).r;
    float farDepth = texture(eminusDepthTex0, texcoord).r;
    bool lod = eminusRenderDistance > 0;
    float fogEnd = lod ? float(eminusRenderDistance) : far;

    float blocks;
    if (nearDepth < SKY_DEPTH) {
        blocks = viewDistance(nearDepth, gbufferProjectionInverse);
    } else if (lod && farDepth < SKY_DEPTH) {
        blocks = viewDistance(farDepth, eminusProjectionInverse);
    } else {
        return;
    }

    color.rgb = mix(color.rgb, fogColor, smoothstep(fogEnd * FOG_START, fogEnd, blocks));
}
