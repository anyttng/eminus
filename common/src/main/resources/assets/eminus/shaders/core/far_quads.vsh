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
};

uniform usamplerBuffer Quads;
uniform usamplerBuffer MeshRecords;
uniform samplerBuffer ModelRecords;
uniform usamplerBuffer TintColours;
uniform sampler2D Lightmap;

#include <eminus:far_vertex.glsl>

layout(location = 0) out vec2 faceUV;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) flat out vec3 tintColour;
layout(location = 3) flat out ivec2 atlasCell;

#ifdef NEAR_SECTIONS
layout(location = 4) out vec3 nearPoint;
#endif

const float FACE_SHADE[8] = float[8](
    SHADE_DOWN, SHADE_UP, SHADE_NORTH_SOUTH, SHADE_NORTH_SOUTH, SHADE_WEST_EAST, SHADE_WEST_EAST,
    SHADE_BLADE, SHADE_BLADE);
const int LIGHT_STEP = 16;

void main() {
    FarVertex vertex = far_vertex(gl_VertexIndex);
    gl_Position = FarProjView * vec4(vertex.position, 1.0);

#ifdef NEAR_SECTIONS
    nearPoint = vertex.facePoint;
#endif

    faceUV = vertex.faceUV;
    atlasCell = vertex.atlasCell;
    tintColour = vertex.tint;

    vec4 colour = sample_lightmap(Lightmap, ivec2(vertex.blockLight * LIGHT_STEP, vertex.skyLight * LIGHT_STEP));
    colour.rgb *= FACE_SHADE[vertex.face];
    vertexColor = colour;
}
