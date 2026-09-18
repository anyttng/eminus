#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:sample_lightmap.glsl>

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
};

uniform usamplerBuffer Quads;
uniform usamplerBuffer MeshRecords;
uniform samplerBuffer ModelRecords;
uniform sampler2D Lightmap;

#include <eminus:far_vertex.glsl>

layout(location = 0) out vec2 faceUV;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) flat out vec3 tintColour;
layout(location = 3) flat out ivec2 atlasCell;
layout(location = 5) flat out ivec4 variantInfo;
layout(location = 6) flat out ivec3 cellOrigin;
layout(location = 7) out vec3 voxelPoint;

#ifdef NEAR_SECTIONS
layout(location = 4) out vec3 nearPoint;
#endif

const int LIGHT_STEP = 16;

void main() {
    FarVertex vertex = far_vertex(gl_VertexIndex);
    gl_Position = FarProjView * vec4(vertex.position, 1.0);

#ifdef NEAR_SECTIONS
    nearPoint = vertex.facePoint;
#endif

    faceUV = vertex.faceUV;
    atlasCell = vertex.atlasCell;
    variantInfo = ivec4(vertex.variantStart, vertex.variantCount, vertex.faceSlot, vertex.level);
    cellOrigin = vertex.cellOrigin;
    voxelPoint = vertex.voxelPoint;
    tintColour = vertex.tint;

    vec4 colour = sample_lightmap(Lightmap, ivec2(vertex.blockLight * LIGHT_STEP, vertex.skyLight * LIGHT_STEP));
    float faceShade = vertex.face == 0 ? ShadeDown
        : vertex.face == 1 ? ShadeUp
        : vertex.face == 2 ? ShadeNorth
        : vertex.face == 3 ? ShadeSouth
        : vertex.face == 4 ? ShadeWest
        : vertex.face == 5 ? ShadeEast
        : SHADE_BLADE;
    colour.rgb *= faceShade;
    vertexColor = colour;
}
