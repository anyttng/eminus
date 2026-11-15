#version 330
#extension GL_ARB_separate_shader_objects : require

#include <eminus:far_frame.glsl>

uniform sampler2D EminusAtlas;
uniform sampler2D EminusTintMask;
uniform usamplerBuffer EminusModelVariants;

#include <eminus:far_surface.glsl>
#include <eminus:far_variant.glsl>
#include <eminus:far_corners.glsl>
#include <eminus:far_alpha.glsl>

layout(location = 0) in vec2 faceUV;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in vec3 tintColor;
layout(location = 3) flat in ivec2 atlasCell;
layout(location = 5) flat in ivec4 variantInfo;
layout(location = 6) flat in ivec3 cellOrigin;
layout(location = 7) in vec3 voxelPoint;
layout(location = 8) in vec3 occlusionPoint;
layout(location = 9) flat in vec2 occlusionSize;
layout(location = 10) flat in uvec2 occlusionWord;

#ifdef NEAR_SECTIONS
uniform usamplerBuffer EminusNearSections;
layout(location = 4) in vec3 nearPoint;

#include <eminus:far_near.glsl>
#endif

#if defined(IN_FRONT_OF_OPAQUE) || defined(BEHIND_FRONT)
#include <eminus:far_depth.glsl>
#endif

#ifdef IN_FRONT_OF_OPAQUE
uniform sampler2D EminusOpaqueDepth;
#endif

#ifdef BEHIND_FRONT
uniform sampler2D EminusFrontDepth;
#endif

layout(location = 0) out vec4 fragColor;

void main() {
#ifdef DEPTH_ONLY
    fragColor = vec4(0.0);
    return;
#endif
    vec2 occlusionFootprint = fwidth(occlusionPoint.xy);
#ifdef NEAR_SECTIONS
    if (eminus_in_near_section(nearPoint)) {
        discard;
    }
#endif
#ifdef IN_FRONT_OF_OPAQUE
    if (NEARER(texelFetch(EminusOpaqueDepth, ivec2(gl_FragCoord.xy), 0).r, gl_FragCoord.z)) {
        discard;
    }
#endif
#ifdef BEHIND_FRONT
    if (!NEARER(texelFetch(EminusFrontDepth, ivec2(gl_FragCoord.xy), 0).r, gl_FragCoord.z)) {
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

    vec4 color = eminus_surface(cell, faceUV, tintColor) * vertexColor;
#ifndef SOLID
#ifdef TRANSLUCENT_PASS
    bool kept = eminus_alpha_kept(color.a, EminusTranslucentAlphaCompare, EminusTranslucentAlphaReference);
#else
    bool kept = eminus_alpha_kept(color.a, EminusOpaqueAlphaCompare, EminusOpaqueAlphaReference);
#endif
    if (!kept) {
        discard;
    }
#endif

    color.rgb *= eminus_corner_occlusion(occlusionWord.x, occlusionPoint.xy, occlusionSize, occlusionWord.y != 0u,
            occlusionFootprint, occlusionPoint.z);

#ifdef FULL_COVERAGE
    fragColor = vec4(color.rgb, 1.0);
#else
    fragColor = color;
#endif
}
