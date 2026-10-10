#version 330

#include <eminus:far_frame.glsl>

uniform usamplerBuffer Quads;
uniform usamplerBuffer MeshRecords;
uniform samplerBuffer ModelRecords;

#include <eminus:far_vertex.glsl>

out vec2 eminus_faceUV;
out vec3 eminus_nearPoint;
out vec3 eminus_voxelPoint;
out vec3 eminus_viewPos;
out vec3 eminus_playerPos;
out vec2 eminus_lmcoord;
flat out vec3 eminus_tint;
flat out vec3 eminus_normal;
flat out ivec2 eminus_atlasCell;
flat out ivec4 eminus_variantInfo;
flat out ivec3 eminus_cellOrigin;
flat out int eminus_face;
flat out float eminus_emission;
flat out int eminus_blockId;
#ifdef PACK_VERTEX
struct EminusVertex {
    vec3 playerPos;
    int blockId;
    int face;
    bool blade;
    bool top;
    vec2 lmcoord;
    int level;
    bool shadow;
};

vec4 eminus_project(vec3 playerPos) {
    return FarProjView * vec4(playerPos, 1.0);
}

vec4 eminus_vertexPosition(inout EminusVertex vertex);
#elif defined PACK_SHADOW_VERTEX
vec4 eminus_shadowPosition(vec4 shadowClipPosition);
#elif defined DH_PROGRAM
uniform sampler2D Atlas;
uniform sampler2D TintMask;
#endif
#ifndef DH_PROGRAM
out vec3 iris_vBlockPos;
flat out uvec2 iris_TexId;
#endif

const vec4 CULLED_POSITION = vec4(2.0, 2.0, 2.0, 1.0);
const vec3 BLADE_NORMAL = vec3(0.0, 1.0, 0.0);
const float LIGHT_LEVELS = 16.0;
const float LIGHT_CENTRE = 0.5;
const int LAST_AXIS_FACE = 5;
const int FAR_FIRST_SIDE_FACE = 2;
const int FAR_FIRST_TOP_CORNER = 2;
#ifdef SHADOW_PASS
const bool FAR_SHADOW_PASS = true;
#else
const bool FAR_SHADOW_PASS = false;
#endif
#ifdef STILL
const bool FAR_ANIMATED = false;
#else
const bool FAR_ANIMATED = true;
#endif
const int FAR_NO_BLOCK_ID = -1;
const int FAR_ANIMATED_LEVEL = 0;

#ifdef DH_PROGRAM
bool eminus_culled;

vec4 eminus_faceColour(ivec2 atlasCell, vec3 tint) {
    vec4 colour = texelFetch(Atlas, atlasCell, FACE_MEAN_LEVEL);
    colour.rgb *= mix(vec3(1.0), tint, texelFetch(TintMask, atlasCell, FACE_MEAN_LEVEL).r);
#ifdef FULL_COVERAGE
    colour.a = 1.0;
#endif
    return colour;
}

void eminus_cull() {
    if (eminus_culled) {
        gl_Position = CULLED_POSITION;
    }
}
#endif

void eminus_vertex() {
    FarVertex vertex = far_vertex(gl_VertexIndex);
    vec3 position = vertex.position;
    vec2 lmcoord = (vec2(vertex.blockLight, vertex.skyLight) + LIGHT_CENTRE) / LIGHT_LEVELS;
    int blockId = int(texelFetch(ModelRecords, vertex.modelId * MODEL_TEXELS + PACK_ID_TEXEL).x);
#ifdef PACK_VERTEX
    bool animated = FAR_ANIMATED && vertex.level == FAR_ANIMATED_LEVEL;
    bool top = animated && vertex.face >= FAR_FIRST_SIDE_FACE
            && gl_VertexIndex % FAR_CORNERS_PER_QUAD >= FAR_FIRST_TOP_CORNER;
    EminusVertex hooked = EminusVertex(position, animated ? blockId : FAR_NO_BLOCK_ID, vertex.face,
            animated && vertex.face >= FIRST_BLADE_FACE, top, lmcoord, vertex.level, FAR_SHADOW_PASS);
    vec4 clipPosition = eminus_vertexPosition(hooked);
    position = hooked.playerPos;
    gl_Position = vertex.culled ? CULLED_POSITION : clipPosition;
#elif defined PACK_SHADOW_VERTEX
    gl_Position = vertex.culled ? CULLED_POSITION : eminus_shadowPosition(FarProjView * vec4(position, 1.0));
#elif !defined DH_PROGRAM
    gl_Position = vertex.culled ? CULLED_POSITION : FarProjView * vec4(position, 1.0);
#endif
#ifdef SHADOW_ZERO_TO_ONE
    // The remap Iris gives a patched shadow program, so a pack's -1..1 shadow clip lands where its lookups read.
    gl_Position.z = 0.5 * (gl_Position.z + gl_Position.w);
#endif

    eminus_lmcoord = lmcoord;
    eminus_blockId = blockId;
    eminus_faceUV = vertex.faceUV;
    eminus_nearPoint = vertex.facePoint;
    eminus_voxelPoint = vertex.voxelPoint;
    eminus_viewPos = (FarView * vec4(position, 1.0)).xyz;
    eminus_playerPos = position;
    eminus_tint = vertex.tint;
    vec3 normal = vertex.face < FIRST_BLADE_FACE ? far_face_normal(vertex.face) : BLADE_NORMAL;
    eminus_normal = mat3(FarView) * normal;
    eminus_atlasCell = vertex.atlasCell;
    eminus_variantInfo = ivec4(vertex.variantStart, vertex.variantCount, vertex.faceSlot, vertex.level);
    eminus_cellOrigin = vertex.cellOrigin;
    eminus_face = vertex.face;
    eminus_emission = float(vertex.emission) / float(MAX_EMISSION);

#ifdef DH_PROGRAM
    eminus_culled = vertex.culled;
    _vert_position = vertex.position;
    _vert_normal = normal;
    _vert_tex_light_coord = lmcoord;
    _vert_color = eminus_faceColour(vertex.atlasCell, vertex.tint);
    dhMaterialId = blockId;
#else
    iris_vBlockPos = vertex.voxelPoint;
    iris_TexId = uvec2(0u, uint(min(vertex.face, LAST_AXIS_FACE)));
#endif
}

#if !defined DH_PROGRAM
void main() {
    eminus_vertex();
}
#endif
