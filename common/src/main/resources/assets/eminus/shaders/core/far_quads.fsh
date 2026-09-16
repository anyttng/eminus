#version 330

#moj_import <minecraft:globals.glsl>

layout(std140) uniform FarFrame {
    mat4 FarProjView;
    int MinBlockY;
    int AtlasCells;
    int NearSide;
    int NearHeight;
    ivec3 NearOrigin;
};

uniform sampler2D Atlas;
uniform sampler2D TintMask;
uniform sampler2D NearMask;

in vec2 faceUV;
in vec4 vertexColor;
flat in vec3 tintColour;
flat in ivec2 atlasCell;

#ifdef NEAR_SECTIONS
uniform usamplerBuffer NearSections;
in vec3 nearPoint;
#endif

out vec4 fragColor;

void main() {
    if (gl_FragCoord.z > texelFetch(NearMask, ivec2(gl_FragCoord.xy), 0).r) {
        discard;
    }

#ifdef NEAR_SECTIONS
    ivec3 section = CameraBlockPos + ivec3(floor(nearPoint)) - NearOrigin;
    if (all(greaterThanEqual(section, ivec3(0)))) {
        section /= NEAR_SECTION_BLOCKS;
        if (section.x < NearSide && section.y < NearHeight && section.z < NearSide) {
            int index = (section.z * NearSide + section.x) * NearHeight + section.y;
            uint bits = texelFetch(NearSections, index >> NEAR_TEXEL_SHIFT).r;
            if (((bits >> uint(index & (NEAR_TEXEL_BITS - 1))) & 1u) != 0u) {
                discard;
            }
        }
    }
#endif

    vec2 cell = vec2(1.0) / float(AtlasCells);
    float margin = 0.5 / float(FACE_SIDE);
    vec2 within = clamp(fract(faceUV), margin, 1.0 - margin);
    vec2 uv = (vec2(atlasCell) + within) * cell;

    vec2 gradX = dFdx(faceUV) * cell;
    vec2 gradY = dFdy(faceUV) * cell;

    vec4 colour = textureGrad(Atlas, uv, gradX, gradY) * vertexColor;
    if (colour.a < ALPHA_CUTOUT) {
        discard;
    }

    colour.rgb *= mix(vec3(1.0), tintColour, textureGrad(TintMask, uv, gradX, gradY).r);

#ifdef FULL_COVERAGE
    fragColor = vec4(colour.rgb, 1.0);
#else
    fragColor = colour;
#endif
}
