#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform FarFrame {
    mat4 FarProjView;
    int MinBlockY;
    int AtlasCells;
    int NearSide;
    int NearHeight;
    ivec3 NearOrigin;
    float ShadeDown;
    float ShadeUp;
    float ShadeNorth;
    float ShadeSouth;
    float ShadeWest;
    float ShadeEast;
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
};

uniform sampler2D Atlas;
uniform sampler2D TintMask;
uniform usamplerBuffer ModelVariants;

#include <eminus:far_surface.glsl>
#include <eminus:far_variant.glsl>

layout(location = 0) in vec2 faceUV;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in vec3 tintColour;
layout(location = 3) flat in ivec2 atlasCell;
layout(location = 5) flat in ivec4 variantInfo;
layout(location = 6) flat in ivec3 cellOrigin;
layout(location = 7) in vec3 voxelPoint;

#ifdef NEAR_SECTIONS
uniform usamplerBuffer NearSections;
layout(location = 4) in vec3 nearPoint;
#endif

layout(location = 0) out vec4 fragColor;

void main() {
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

    ivec2 cell = atlasCell;
    if (variantInfo.y > 0) {
        ivec3 voxel = clamp(ivec3(floor(voxelPoint)), ivec3(0), ivec3(VOXELS_PER_SIDE - 1));
        int slot = far_variant_model(variantInfo.x, variantInfo.y, cellOrigin + (voxel << variantInfo.w))
                * MODEL_FACES + variantInfo.z;
        cell = ivec2(slot % AtlasCells, slot / AtlasCells);
    }

    vec4 colour = far_surface(cell, faceUV, tintColour) * vertexColor;
    if (colour.a < ALPHA_CUTOUT) {
        discard;
    }

#ifdef FULL_COVERAGE
    fragColor = vec4(colour.rgb, 1.0);
#else
    fragColor = colour;
#endif
}
