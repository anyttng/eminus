#version 330

#include <eminus:far_frame.glsl>

uniform sampler2D Atlas;
uniform sampler2D TintMask;
uniform usamplerBuffer ModelVariants;
uniform usamplerBuffer NearSections;

#include <eminus:far_surface.glsl>
#include <eminus:far_variant.glsl>
#include <eminus:far_near.glsl>

in vec2 eminus_faceUV;
in vec3 eminus_nearPoint;
in vec3 eminus_voxelPoint;
in vec3 eminus_viewPos;
in vec3 eminus_playerPos;
in vec2 eminus_lmcoord;
flat in vec3 eminus_tint;
flat in vec3 eminus_normal;
flat in ivec2 eminus_atlasCell;
flat in ivec4 eminus_variantInfo;
flat in ivec3 eminus_cellOrigin;
flat in int eminus_face;
flat in float eminus_emission;
flat in int eminus_blockId;

struct EminusFragment {
    vec4 color;
    vec2 texcoord;
    vec2 texcoordDx;
    vec2 texcoordDy;
    vec2 lmcoord;
    vec4 glcolor;
    vec3 normal;
    int face;
    vec3 viewPos;
    vec3 playerPos;
    float emission;
    int blockId;
    bool translucent;
    bool blade;
};

EminusFragment eminus_fragment() {
    if (far_in_near_section(eminus_nearPoint)) {
        discard;
    }

    ivec2 cell = eminus_atlasCell;
    if (eminus_variantInfo.y > 0) {
        ivec3 voxel = clamp(ivec3(floor(eminus_voxelPoint)), ivec3(0), ivec3(VOXELS_PER_SIDE - 1));
        int slot = far_variant_model(eminus_variantInfo.x, eminus_variantInfo.y,
                eminus_cellOrigin + (voxel << eminus_variantInfo.w)) * MODEL_FACES + eminus_variantInfo.z;
        cell = ivec2(slot % AtlasCells, slot / AtlasCells);
    }

    vec4 colour = far_surface(cell, eminus_faceUV, eminus_tint);
    if (colour.a < ALPHA_CUTOUT) {
        discard;
    }

    float atlasCell = 1.0 / float(AtlasCells);
    float margin = 0.5 / float(FACE_SIDE);
    EminusFragment fragment;
#ifdef FULL_COVERAGE
    fragment.color = vec4(colour.rgb, 1.0);
#else
    fragment.color = colour;
#endif
    fragment.texcoord = (vec2(cell) + clamp(fract(eminus_faceUV), margin, 1.0 - margin)) * atlasCell;
    fragment.texcoordDx = dFdx(eminus_faceUV) * atlasCell;
    fragment.texcoordDy = dFdy(eminus_faceUV) * atlasCell;
    fragment.lmcoord = eminus_lmcoord;
    fragment.glcolor = vec4(eminus_tint, 1.0);
    fragment.normal = eminus_normal;
    fragment.face = eminus_face;
    fragment.viewPos = eminus_viewPos;
    fragment.playerPos = eminus_playerPos;
    fragment.emission = eminus_emission;
    fragment.blockId = eminus_blockId;
#ifdef TRANSLUCENT_PASS
    fragment.translucent = true;
#else
    fragment.translucent = false;
#endif
    fragment.blade = eminus_face >= FIRST_BLADE_FACE;
    return fragment;
}
