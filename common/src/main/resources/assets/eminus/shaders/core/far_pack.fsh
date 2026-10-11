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

#ifdef PACK_SHADOW_PROGRAM
#include <eminus:far_pack_inputs.glsl>

ivec2 eminus_cell;
#endif

EminusFragment eminus_fragment() {
#if !defined DH_PROGRAM || defined PACK_SHADOW_PROGRAM
    if (far_in_near_section(eminus_nearPoint)) {
        discard;
    }
#endif

    ivec2 cell = eminus_atlasCell;
    if (eminus_variantInfo.y > 0) {
        ivec3 voxel = clamp(ivec3(floor(eminus_voxelPoint)), ivec3(0), ivec3(VOXELS_PER_SIDE - 1));
        int slot = far_variant_model(eminus_variantInfo.x, eminus_variantInfo.y,
                eminus_cellOrigin + (voxel << eminus_variantInfo.w)) * MODEL_FACES + eminus_variantInfo.z;
        cell = ivec2(slot % AtlasCells, slot / AtlasCells);
    }
#ifdef PACK_SHADOW_PROGRAM
    eminus_cell = cell;
#endif

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

#ifdef PACK_SHADOW_PROGRAM
vec2 eminus_albedoUV(vec2 faceUV) {
    float margin = 0.5 / float(FACE_SIDE);
    return (vec2(eminus_cell) + clamp(fract(faceUV), margin, 1.0 - margin)) / float(AtlasCells);
}

vec4 eminus_albedoTinted(vec4 colour, float mask) {
    colour.rgb *= mix(vec3(1.0), eminus_tint, mask);
    return colour;
}

vec4 eminus_albedo(sampler2D packTexture, vec2 faceUV) {
    return far_surface(eminus_cell, faceUV, eminus_tint);
}

vec4 eminus_albedo(sampler2D packTexture, vec2 faceUV, float bias) {
    return far_surface(eminus_cell, faceUV, eminus_tint);
}

vec4 eminus_albedoLod(sampler2D packTexture, vec2 faceUV, float lod) {
    vec2 uv = eminus_albedoUV(faceUV);
    return eminus_albedoTinted(textureLod(Atlas, uv, lod), textureLod(TintMask, uv, lod).r);
}

vec4 eminus_albedoGrad(sampler2D packTexture, vec2 faceUV, vec2 dx, vec2 dy) {
    vec2 uv = eminus_albedoUV(faceUV);
    float cell = 1.0 / float(AtlasCells);
    return eminus_albedoTinted(textureGrad(Atlas, uv, dx * cell, dy * cell),
            textureGrad(TintMask, uv, dx * cell, dy * cell).r);
}
#endif
