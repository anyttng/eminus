#version 330

layout(std140) uniform FarFrame {
    mat4 FarProjView;
    int MinBlockY;
    int AtlasCells;
};

uniform sampler2D Atlas;
uniform sampler2D Coverage;

in vec2 faceUV;
in vec4 vertexColor;
flat in ivec2 atlasCell;

out vec4 fragColor;

void main() {
    if (gl_FragCoord.z > texelFetch(Coverage, ivec2(gl_FragCoord.xy), 0).r) {
        discard;
    }

    vec2 cell = vec2(1.0) / float(AtlasCells);
    float margin = 0.5 / float(FACE_SIDE);
    vec2 within = clamp(fract(faceUV), margin, 1.0 - margin);
    vec2 uv = (vec2(atlasCell) + within) * cell;

    vec4 colour = textureGrad(Atlas, uv, dFdx(faceUV) * cell, dFdy(faceUV) * cell) * vertexColor;
    if (colour.a < ALPHA_CUTOUT) {
        discard;
    }

#ifdef FULL_COVERAGE
    fragColor = vec4(colour.rgb, 1.0);
#else
    fragColor = colour;
#endif
}
