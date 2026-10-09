#version 330

#moj_import <eminus:far_frame.glsl>

uniform usamplerBuffer Quads;
uniform usamplerBuffer MeshRecords;
uniform samplerBuffer ModelRecords;

#moj_import <eminus:far_vertex.glsl>

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
#elif defined DH_PROGRAM
uniform sampler2D Atlas;
uniform sampler2D TintMask;
#else
out vec3 iris_vBlockPos;
flat out uvec2 iris_TexId;
#endif

const vec4 CULLED_POSITION = vec4(2.0, 2.0, 2.0, 1.0);
const vec3 BLADE_NORMAL = vec3(0.0, 1.0, 0.0);
const float LIGHT_LEVELS = 16.0;
const float LIGHT_CENTRE = 0.5;
const int LAST_AXIS_FACE = 5;

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
    FarVertex vertex = far_vertex(gl_VertexID);
#ifdef PACK_VERTEX
    gl_Position = vertex.culled ? CULLED_POSITION : eminus_shadowPosition(FarProjView * vec4(vertex.position, 1.0));
#elif !defined DH_PROGRAM
    gl_Position = vertex.culled ? CULLED_POSITION : FarProjView * vec4(vertex.position, 1.0);
#endif

    eminus_faceUV = vertex.faceUV;
    eminus_nearPoint = vertex.facePoint;
    eminus_voxelPoint = vertex.voxelPoint;
    eminus_viewPos = (FarView * vec4(vertex.position, 1.0)).xyz;
    eminus_playerPos = vertex.position;
    eminus_lmcoord = (vec2(vertex.blockLight, vertex.skyLight) + LIGHT_CENTRE) / LIGHT_LEVELS;
    eminus_tint = vertex.tint;
    vec3 normal = vertex.face < FIRST_BLADE_FACE ? far_face_normal(vertex.face) : BLADE_NORMAL;
    eminus_normal = mat3(FarView) * normal;
    eminus_atlasCell = vertex.atlasCell;
    eminus_variantInfo = ivec4(vertex.variantStart, vertex.variantCount, vertex.faceSlot, vertex.level);
    eminus_cellOrigin = vertex.cellOrigin;
    eminus_face = vertex.face;
    eminus_emission = float(vertex.emission) / float(MAX_EMISSION);
    eminus_blockId = int(texelFetch(ModelRecords, vertex.modelId * MODEL_TEXELS + PACK_ID_TEXEL).x);

#ifdef DH_PROGRAM
    eminus_culled = vertex.culled;
    _vert_position = vertex.position;
    _vert_normal = normal;
    _vert_tex_light_coord = eminus_lmcoord;
    _vert_color = eminus_faceColour(vertex.atlasCell, vertex.tint);
    dhMaterialId = eminus_blockId;
#elif !defined PACK_VERTEX
    iris_vBlockPos = vertex.voxelPoint;
    iris_TexId = uvec2(0u, uint(min(vertex.face, LAST_AXIS_FACE)));
#endif
}

#if !defined DH_PROGRAM
void main() {
    eminus_vertex();
}
#endif
