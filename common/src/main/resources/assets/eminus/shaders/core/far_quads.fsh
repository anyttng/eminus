#version 330
#extension GL_ARB_separate_shader_objects : require

#include <eminus:far_frame.glsl>

uniform sampler2D EminusAtlas;
uniform sampler2D EminusTintMask;
uniform usamplerBuffer EminusModelVariants;

#include <eminus:far_surface.glsl>
#include <eminus:far_variant.glsl>
#include <eminus:far_corners.glsl>

layout(location = 0) in vec2 faceUV;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in vec3 tintColour;
layout(location = 3) flat in ivec2 atlasCell;
layout(location = 5) flat in ivec4 variantInfo;
layout(location = 6) flat in ivec3 cellOrigin;
layout(location = 7) in vec3 voxelPoint;
layout(location = 8) in vec2 occlusionPoint;
layout(location = 9) flat in vec2 occlusionSize;
layout(location = 10) flat in uvec2 occlusionWord;

#ifdef NEAR_SECTIONS
uniform usamplerBuffer EminusNearSections;
layout(location = 4) in vec3 nearPoint;

#include <eminus:far_near.glsl>
#endif

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 occlusionFootprint = fwidth(occlusionPoint);
#ifdef NEAR_SECTIONS
    if (eminus_in_near_section(nearPoint)) {
        discard;
    }
#endif

    ivec2 cell = atlasCell;
    if (variantInfo.y > 0) {
        ivec3 voxel = clamp(ivec3(floor(voxelPoint)), ivec3(0), ivec3(VOXELS_PER_SIDE - 1));
        int slot = eminus_variant_model(variantInfo.x, variantInfo.y, cellOrigin + (voxel << variantInfo.w))
                * MODEL_FACES + variantInfo.z;
        cell = ivec2(slot % EminusAtlasCells, slot / EminusAtlasCells);
    }

    vec4 colour = eminus_surface(cell, faceUV, tintColour) * vertexColor;
#ifndef SOLID
    if (colour.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    colour.rgb *= eminus_corner_occlusion(occlusionWord.x, occlusionPoint, occlusionSize, occlusionWord.y != 0u,
            occlusionFootprint);

#ifdef FULL_COVERAGE
    fragColor = vec4(colour.rgb, 1.0);
#else
    fragColor = colour;
#endif
}
