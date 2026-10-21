#version 330

#moj_import <eminus:far_frame.glsl>

uniform sampler2D EminusAtlas;
uniform sampler2D EminusTintMask;
uniform usamplerBuffer EminusModelVariants;
uniform usamplerBuffer EminusNearSections;

#moj_import <eminus:far_surface.glsl>
#moj_import <eminus:far_variant.glsl>
#moj_import <eminus:far_near.glsl>
#moj_import <eminus:far_corners.glsl>

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
#ifndef DH_PROGRAM
in vec2 eminus_occlusionPoint;
flat in vec2 eminus_occlusionSize;
flat in uvec2 eminus_occlusion;
#endif

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
#moj_import <eminus:far_pack_inputs.glsl>

ivec2 eminus_cell;
#endif

EminusFragment eminus_fragment() {
#ifndef DH_PROGRAM
    vec2 occlusionFootprint = fwidth(eminus_occlusionPoint);
#endif
#if !defined DH_PROGRAM || defined PACK_SHADOW_PROGRAM
    if (eminus_in_near_section(eminus_nearPoint)) {
        discard;
    }
#endif

    ivec2 cell = eminus_atlasCell;
    if (eminus_variantInfo.y > 0) {
        ivec3 voxel = clamp(ivec3(floor(eminus_voxelPoint)), ivec3(0), ivec3(VOXELS_PER_SIDE - 1));
        int slot = eminus_variant_model(eminus_variantInfo.x, eminus_variantInfo.y,
                eminus_cellOrigin + (voxel << eminus_variantInfo.w)) * MODEL_FACES + eminus_variantInfo.z;
        cell = ivec2(slot % EminusAtlasCells, slot / EminusAtlasCells);
    }
#ifdef PACK_SHADOW_PROGRAM
    eminus_cell = cell;
#endif

    vec4 color = eminus_surface(cell, eminus_faceUV, eminus_tint);
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }

    float atlasCell = 1.0 / float(EminusAtlasCells);
    float margin = 0.5 / float(EminusAtlasCellSide);
    EminusFragment fragment;
#ifdef FULL_COVERAGE
    fragment.color = vec4(color.rgb, 1.0);
#else
    fragment.color = color;
#endif
    fragment.texcoord = (vec2(cell) + clamp(fract(eminus_faceUV), margin, 1.0 - margin)) * atlasCell;
    fragment.texcoordDx = dFdx(eminus_faceUV) * atlasCell;
    fragment.texcoordDy = dFdy(eminus_faceUV) * atlasCell;
    fragment.lmcoord = eminus_lmcoord;
#ifdef DH_PROGRAM
    fragment.glcolor = vec4(eminus_tint, 1.0);
#else
    float occlusion = eminus_corner_occlusion(eminus_occlusion.x, eminus_occlusionPoint, eminus_occlusionSize,
            eminus_occlusion.y != 0u, occlusionFootprint);
#ifdef SEPARATE_AO
    fragment.glcolor = vec4(eminus_tint, occlusion);
#else
    fragment.glcolor = vec4(eminus_tint * occlusion, 1.0);
#endif
#endif
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
    float margin = 0.5 / float(EminusAtlasCellSide);
    return (vec2(eminus_cell) + clamp(fract(faceUV), margin, 1.0 - margin)) / float(EminusAtlasCells);
}

vec4 eminus_albedoTinted(vec4 color, float mask) {
    color.rgb *= mix(vec3(1.0), eminus_tint, mask);
    return color;
}

vec4 eminus_albedo(sampler2D packTexture, vec2 faceUV) {
    return eminus_surface(eminus_cell, faceUV, eminus_tint);
}

vec4 eminus_albedo(sampler2D packTexture, vec2 faceUV, float bias) {
    return eminus_surface(eminus_cell, faceUV, eminus_tint);
}

vec4 eminus_texelNearest(sampler2D source, vec2 uv, int level) {
    ivec2 size = textureSize(source, level);
    return texelFetch(source, clamp(ivec2(floor(uv * vec2(size))), ivec2(0), size - 1), level);
}

// GL_NEAREST_MIPMAP_LINEAR by hand: the atlas sampler follows the game's terrain filter.
vec4 eminus_nearestMipped(sampler2D source, vec2 uv, float lod) {
    float level = clamp(lod, 0.0, float(EminusAtlasMeanLevel));
    int low = int(floor(level));
    vec4 color = eminus_texelNearest(source, uv, low);
    float blend = level - float(low);
    if (blend <= 0.0) {
        return color;
    }
    return mix(color, eminus_texelNearest(source, uv, low + 1), blend);
}

vec4 eminus_albedoNearest(vec2 faceUV, float lod) {
    vec2 uv = eminus_albedoUV(faceUV);
    return eminus_albedoTinted(eminus_nearestMipped(EminusAtlas, uv, lod),
            eminus_nearestMipped(EminusTintMask, uv, lod).r);
}

vec4 eminus_albedoLod(sampler2D packTexture, vec2 faceUV, float lod) {
    return eminus_albedoNearest(faceUV, lod);
}

vec4 eminus_albedoGrad(sampler2D packTexture, vec2 faceUV, vec2 dx, vec2 dy) {
    vec2 size = vec2(textureSize(EminusAtlas, 0)) / float(EminusAtlasCells);
    float footprint = max(length(dx * size), length(dy * size));
    return eminus_albedoNearest(faceUV, log2(max(footprint, EMINUS_MIN_AXIS_LENGTH)));
}
#endif
