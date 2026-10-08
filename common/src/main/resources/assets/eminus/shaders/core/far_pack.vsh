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
vec4 eminus_shadowPosition(vec4 shadowClipPosition);
#else
out vec3 iris_vBlockPos;
flat out uvec2 iris_TexId;
#endif

const vec4 CULLED_POSITION = vec4(2.0, 2.0, 2.0, 1.0);
const vec3 BLADE_NORMAL = vec3(0.0, 1.0, 0.0);
const float LIGHT_LEVELS = 16.0;
const float LIGHT_CENTRE = 0.5;
const int LAST_AXIS_FACE = 5;

void main() {
    FarVertex vertex = far_vertex(gl_VertexIndex);
#ifdef PACK_VERTEX
    gl_Position = vertex.culled ? CULLED_POSITION : eminus_shadowPosition(FarProjView * vec4(vertex.position, 1.0));
#else
    gl_Position = vertex.culled ? CULLED_POSITION : FarProjView * vec4(vertex.position, 1.0);
#endif

    eminus_faceUV = vertex.faceUV;
    eminus_nearPoint = vertex.facePoint;
    eminus_voxelPoint = vertex.voxelPoint;
    eminus_viewPos = (FarView * vec4(vertex.position, 1.0)).xyz;
    eminus_playerPos = vertex.position;
    eminus_lmcoord = (vec2(vertex.blockLight, vertex.skyLight) + LIGHT_CENTRE) / LIGHT_LEVELS;
    eminus_tint = vertex.tint;
    eminus_normal = mat3(FarView) * (vertex.face < FIRST_BLADE_FACE ? far_face_normal(vertex.face) : BLADE_NORMAL);
    eminus_atlasCell = vertex.atlasCell;
    eminus_variantInfo = ivec4(vertex.variantStart, vertex.variantCount, vertex.faceSlot, vertex.level);
    eminus_cellOrigin = vertex.cellOrigin;
    eminus_face = vertex.face;
    eminus_emission = float(vertex.emission) / float(MAX_EMISSION);
    eminus_blockId = int(texelFetch(ModelRecords, vertex.modelId * MODEL_TEXELS + PACK_ID_TEXEL).x);

#ifndef PACK_VERTEX
    iris_vBlockPos = vertex.voxelPoint;
    iris_TexId = uvec2(0u, uint(min(vertex.face, LAST_AXIS_FACE)));
#endif
}
