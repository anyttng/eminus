#version 330

#moj_import <eminus:far_frame.glsl>

uniform sampler2D EminusAtlas;
uniform sampler2D EminusTintMask;
uniform usamplerBuffer EminusModelVariants;

#moj_import <eminus:far_surface.glsl>
#moj_import <eminus:far_variant.glsl>
#moj_import <eminus:far_corners.glsl>

in vec2 faceUV;
in vec4 vertexColor;
flat in vec3 tintColour;
flat in ivec2 atlasCell;
flat in ivec4 variantInfo;
flat in ivec3 cellOrigin;
in vec3 voxelPoint;
in vec2 occlusionPoint;
flat in vec2 occlusionSize;
flat in uvec2 occlusionWord;

#ifdef NEAR_SECTIONS
uniform usamplerBuffer EminusNearSections;
in vec3 nearPoint;

#moj_import <eminus:far_near.glsl>
#endif

out vec4 fragColor;

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
    if (colour.a < ALPHA_CUTOUT) {
        discard;
    }

    colour.rgb *= eminus_corner_occlusion(occlusionWord.x, occlusionPoint, occlusionSize, occlusionWord.y != 0u,
            occlusionFootprint);

#ifdef FULL_COVERAGE
    fragColor = vec4(colour.rgb, 1.0);
#else
    fragColor = colour;
#endif
}
