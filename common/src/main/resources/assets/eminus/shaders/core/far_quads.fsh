#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

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

#include <eminus:far_surface.glsl>

layout(location = 0) in vec2 faceUV;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in vec3 tintColour;
layout(location = 3) flat in ivec2 atlasCell;

#ifdef NEAR_SECTIONS
uniform usamplerBuffer NearSections;
layout(location = 4) in vec3 nearPoint;
#endif

layout(location = 0) out vec4 fragColor;

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

    vec4 colour = far_surface(atlasCell, faceUV, tintColour) * vertexColor;
    if (colour.a < ALPHA_CUTOUT) {
        discard;
    }

#ifdef FULL_COVERAGE
    fragColor = vec4(colour.rgb, 1.0);
#else
    fragColor = colour;
#endif
}
